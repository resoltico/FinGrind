package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Direct Windows control-file transport retaining the exact nofollow native handle.
 *
 * <p>This deliberately does not create a {@code FileChannel} after {@code CreateFileW}. The handle
 * that received the protected owner-only security descriptor is the handle that validates the
 * immutable header, acquires the byte-range lock, and is later unlocked and closed.
 */
final class SqliteWindowsCoordinationFfmTransport {
  private SqliteWindowsCoordinationFfmTransport() {}

  static SqliteCoordinationControlFiles.@Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    return Native.TransportOperations.openOrCreateAndTryExclusiveLock(
        controlPath, magic, position, size);
  }

  static SqliteCoordinationControlFiles.@Nullable LockedControlFile openExistingAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    return Native.TransportOperations.openExistingAndTryExclusiveLock(
        controlPath, magic, position, size);
  }

  static void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
    Native.TransportOperations.createAtomicallySecureRecord(recordPath, magic);
  }

  static void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
    Native.TransportOperations.requireExistingExactRecord(recordPath, magic);
  }

  static String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
    return Native.TransportOperations.physicalObjectIdentity(existingArtifactPath);
  }

  static Path createOrValidatePrivateRoot(Path root) throws IOException {
    return Native.TransportOperations.createOrValidatePrivateRoot(root);
  }

  /** Windows-native implementation retained behind the platform-neutral transport seam. */
  private static final class Native {
    private static final int ERROR_FILE_EXISTS = 80;
    private static final int ERROR_ALREADY_EXISTS = 183;
    private static final int ERROR_INSUFFICIENT_BUFFER = 122;
    private static final int ERROR_LOCK_VIOLATION = 33;

    private static final int GENERIC_READ = 0x8000_0000;
    private static final int GENERIC_WRITE = 0x4000_0000;
    private static final int READ_CONTROL = 0x0002_0000;
    private static final int FILE_READ_ATTRIBUTES = 0x0000_0080;
    private static final int FILE_SHARE_READ_WRITE = 0x0000_0003;
    private static final int CREATE_NEW = 1;
    private static final int OPEN_EXISTING = 3;
    private static final int FILE_ATTRIBUTE_NORMAL = 0x0000_0080;
    private static final int FILE_ATTRIBUTE_DIRECTORY = 0x0000_0010;
    private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x0000_0400;
    private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x0200_0000;
    private static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x0020_0000;
    private static final int FILE_BEGIN = 0;
    private static final int LOCKFILE_FAIL_IMMEDIATELY = 0x0000_0001;
    private static final int LOCKFILE_EXCLUSIVE_LOCK = 0x0000_0002;
    private static final int TOKEN_QUERY = 0x0000_0008;
    private static final int TOKEN_OWNER = 4;
    private static final int SDDL_REVISION_1 = 1;
    private static final int FILE_ID_INFO = 18;
    private static final int FILE_ATTRIBUTE_TAG_INFO = 9;
    private static final int SE_FILE_OBJECT = 1;
    private static final int OWNER_SECURITY_INFORMATION = 0x0000_0001;
    private static final int DACL_SECURITY_INFORMATION = 0x0000_0004;
    private static final short SE_DACL_PROTECTED = 0x1000;
    private static final int ACL_SIZE_INFORMATION = 2;
    private static final int ACCESS_ALLOWED_ACE_TYPE = 0;
    private static final int FILE_ALL_ACCESS = 0x001f_01ff;
    private static final int MAXIMUM_SID_STRING_BYTES = 2_048;

    private static final ReentrantLock BINDING_LOCK = new ReentrantLock();
    private static final ReentrantLock PROCESS_RANGE_LOCK = new ReentrantLock();
    private static final Map<String, List<ProcessRangeLease>> PROCESS_RANGES =
        new ConcurrentHashMap<>();
    private static @Nullable Bindings BINDINGS;

    /**
     * Coordinates the public Windows control-file operations from the exact native-handle facts.
     */
    private static final class TransportOperations {
      static SqliteCoordinationControlFiles.@Nullable LockedControlFile
          openOrCreateAndTryExclusiveLock(Path controlPath, byte[] magic, long position, long size)
              throws IOException {
        Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
        byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
        try (CurrentOwner owner = CurrentOwner.acquire()) {
          NativeHandle handle;
          try {
            handle = FileAdmission.createNewRegularControl(checkedPath, owner);
          } catch (WindowsFailure collision) {
            if (!collision.hasError(ERROR_FILE_EXISTS)
                && !collision.hasError(ERROR_ALREADY_EXISTS)) {
              throw collision;
            }
            handle = FileAdmission.openExistingRegularControl(checkedPath, owner);
          }
          try {
            ControlSecurity.validateRegularOwnerOnlyControl(handle, owner);
            if (handle.created()) {
              ControlContent.writeExact(handle, checkedMagic);
            } else {
              ControlContent.requireExactMagic(handle, checkedMagic);
            }
            return LockOperations.lockOrClose(checkedPath, handle, position, size);
          } catch (IOException | RuntimeException | Error failure) {
            closeHandlePreservingFailure(handle, failure);
            throw failure;
          }
        }
      }

      static SqliteCoordinationControlFiles.@Nullable LockedControlFile
          openExistingAndTryExclusiveLock(Path controlPath, byte[] magic, long position, long size)
              throws IOException {
        Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
        byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
        try (CurrentOwner owner = CurrentOwner.acquire()) {
          NativeHandle handle = FileAdmission.openExistingRegularControl(checkedPath, owner);
          try {
            ControlSecurity.validateRegularOwnerOnlyControl(handle, owner);
            ControlContent.requireExactMagic(handle, checkedMagic);
            return LockOperations.lockOrClose(checkedPath, handle, position, size);
          } catch (IOException | RuntimeException failure) {
            closeHandlePreservingFailure(handle, failure);
            throw failure;
          }
        }
      }

      static void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
        Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
        byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
        try (CurrentOwner owner = CurrentOwner.acquire()) {
          NativeHandle handle = FileAdmission.createNewRegularControl(checkedPath, owner);
          try {
            ControlSecurity.validateRegularOwnerOnlyControl(handle, owner);
            ControlContent.writeExact(handle, checkedMagic);
          } catch (IOException | RuntimeException failure) {
            closeHandlePreservingFailure(handle, failure);
            throw failure;
          }
          closeHandle(handle);
        }
      }

      static void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
        Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
        byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
        try (CurrentOwner owner = CurrentOwner.acquire()) {
          NativeHandle handle = FileAdmission.openExistingRegularControl(checkedPath, owner);
          try {
            ControlSecurity.validateRegularOwnerOnlyControl(handle, owner);
            ControlContent.requireExactMagic(handle, checkedMagic);
          } catch (IOException | RuntimeException failure) {
            closeHandlePreservingFailure(handle, failure);
            throw failure;
          }
          closeHandle(handle);
        }
      }

      /** Returns the exact Windows volume-plus-128-bit-file-id tuple through a nofollow handle. */
      static String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
        Path checkedPath = Objects.requireNonNull(existingArtifactPath, "existingArtifactPath");
        NativeHandle handle = FileAdmission.openExistingFileForIdentity(checkedPath);
        @Nullable Throwable primaryFailure = null;
        try {
          ControlSecurity.validateRegularNonReparse(handle);
          try (Arena arena = Arena.ofConfined()) {
            MemorySegment fileIdInfo = arena.allocate(24L, Long.BYTES);
            NativeResult<Integer> result =
                bindings()
                    .invokeInt(
                        bindings().getFileInformationByHandleEx,
                        handle.segment(),
                        FILE_ID_INFO,
                        fileIdInfo,
                        24);
            requireTrue(result, "GetFileInformationByHandleEx(FileIdInfo)");
            long volumeSerial = fileIdInfo.get(ValueLayout.JAVA_LONG, 0L);
            byte[] fileId = fileIdInfo.asSlice(Long.BYTES, 16L).toArray(ValueLayout.JAVA_BYTE);
            return "windows-v1:volume="
                + Long.toUnsignedString(volumeSerial)
                + ":file="
                + HexFormat.of().formatHex(fileId);
          }
        } catch (IOException | RuntimeException failure) {
          primaryFailure = failure;
          throw failure;
        } catch (Error failure) {
          primaryFailure = failure;
          throw failure;
        } finally {
          if (primaryFailure == null) {
            closeHandle(handle);
          } else {
            closeHandlePreservingFailure(handle, primaryFailure);
          }
        }
      }

      /**
       * Creates or validates the v4 root through one native owner-only directory creation boundary.
       */
      static Path createOrValidatePrivateRoot(Path root) throws IOException {
        Path checkedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        try (CurrentOwner owner = CurrentOwner.acquire()) {
          try {
            FileAdmission.createDirectory(checkedRoot, owner);
          } catch (WindowsFailure collision) {
            if (!collision.hasError(ERROR_ALREADY_EXISTS)) {
              throw collision;
            }
          }
          NativeHandle directory = FileAdmission.openExistingDirectory(checkedRoot);
          try {
            ControlSecurity.validateDirectoryOwnerOnlyControl(directory, owner);
          } catch (IOException | RuntimeException failure) {
            closeHandlePreservingFailure(directory, failure);
            throw failure;
          }
          closeHandle(directory);
        }
        return checkedRoot;
      }
    }

    /** Owns byte-range admission and exact release of a locked Windows control handle. */
    private static final class LockOperations {
      private static SqliteCoordinationControlFiles.@Nullable LockedControlFile lockOrClose(
          Path controlPath, NativeHandle handle, long position, long size) throws IOException {
        @Nullable ProcessRangeLease processRange =
            ProcessRangeLease.tryAcquire(controlPath, position, size);
        if (processRange == null) {
          closeHandle(handle);
          return null;
        }
        try {
          if (!tryLock(handle, position, size)) {
            processRange.close();
            closeHandle(handle);
            return null;
          }
          ProcessRangeLease retainedProcessRange = processRange;
          return SqliteCoordinationControlFiles.lockedControlFile(
              controlPath,
              () -> releaseLockedControl(handle, position, size, retainedProcessRange));
        } catch (IOException | RuntimeException | Error failure) {
          processRange.close();
          throw failure;
        }
      }

      private static void releaseLockedControl(
          NativeHandle handle, long position, long size, ProcessRangeLease processRange)
          throws IOException {
        IOException failure = null;
        try {
          unlock(handle, position, size);
        } catch (IOException exception) {
          failure = exception;
        }
        try {
          closeHandle(handle);
        } catch (IOException exception) {
          if (failure == null) {
            failure = exception;
          } else {
            failure.addSuppressed(exception);
          }
        } finally {
          processRange.close();
        }
        if (failure != null) {
          throw failure;
        }
      }
    }

    /** Opens or creates no-follow Windows control handles with their owner-only descriptor. */
    private static final class FileAdmission {
      private static NativeHandle createNewRegularControl(Path path, CurrentOwner owner)
          throws IOException {
        return createFile(
            path,
            GENERIC_READ | GENERIC_WRITE,
            CREATE_NEW,
            FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT,
            owner,
            true);
      }

      private static NativeHandle openExistingRegularControl(Path path, CurrentOwner owner)
          throws IOException {
        return createFile(
            path,
            GENERIC_READ | GENERIC_WRITE,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT,
            owner,
            false);
      }

      private static NativeHandle openExistingFileForIdentity(Path path) throws IOException {
        return createFile(
            path,
            GENERIC_READ,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT,
            null,
            false);
      }

      private static NativeHandle openExistingDirectory(Path path) throws IOException {
        return createFile(
            path,
            READ_CONTROL | FILE_READ_ATTRIBUTES,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
            null,
            false);
      }

      private static NativeHandle createFile(
          Path path,
          int desiredAccess,
          int creationDisposition,
          int flagsAndAttributes,
          @Nullable CurrentOwner owner,
          boolean newObject)
          throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          @Nullable SecurityAttributes securityAttributes =
              owner == null ? null : owner.securityAttributes(arena);
          @Nullable NativeHandle openedHandle = null;
          try {
            openedHandle =
                openNativeFile(
                    arena,
                    path,
                    desiredAccess,
                    creationDisposition,
                    flagsAndAttributes,
                    securityAttributes,
                    newObject);
          } catch (IOException | RuntimeException | Error failure) {
            closeSecurityAttributesAfterFailedOpen(securityAttributes, openedHandle, failure);
            throw failure;
          }
          NativeHandle createdHandle = Objects.requireNonNull(openedHandle, "openedHandle");
          closeSecurityAttributesAfterSuccessfulOpen(securityAttributes, createdHandle);
          return createdHandle;
        }
      }

      private static NativeHandle openNativeFile(
          Arena arena,
          Path path,
          int desiredAccess,
          int creationDisposition,
          int flagsAndAttributes,
          @Nullable SecurityAttributes securityAttributes,
          boolean newObject)
          throws IOException {
        NativeResult<Long> result =
            bindings()
                .invokeAddress(
                    bindings().createFileW,
                    widePath(arena, path),
                    desiredAccess,
                    FILE_SHARE_READ_WRITE,
                    securityAttributes == null
                        ? MemorySegment.NULL
                        : securityAttributes.attributes(),
                    creationDisposition,
                    flagsAndAttributes,
                    MemorySegment.NULL);
        if (result.value() == -1L) {
          throw windowsFailure("CreateFileW", result.lastError());
        }
        return new NativeHandle(result.value(), newObject);
      }

      private static void closeSecurityAttributesAfterFailedOpen(
          @Nullable SecurityAttributes securityAttributes,
          @Nullable NativeHandle openedHandle,
          Throwable primaryFailure) {
        if (securityAttributes == null) {
          return;
        }
        try {
          securityAttributes.close();
        } catch (IOException descriptorFailure) {
          if (openedHandle != null) {
            closeHandlePreservingFailure(openedHandle, descriptorFailure);
          }
          primaryFailure.addSuppressed(descriptorFailure);
        }
      }

      private static void closeSecurityAttributesAfterSuccessfulOpen(
          @Nullable SecurityAttributes securityAttributes, NativeHandle openedHandle)
          throws IOException {
        if (securityAttributes == null) {
          return;
        }
        try {
          securityAttributes.close();
        } catch (IOException descriptorFailure) {
          closeHandlePreservingFailure(openedHandle, descriptorFailure);
          throw descriptorFailure;
        }
      }

      private static void createDirectory(Path path, CurrentOwner owner) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          SecurityAttributes securityAttributes = owner.securityAttributes(arena);
          try {
            NativeResult<Integer> result =
                bindings()
                    .invokeInt(
                        bindings().createDirectoryW,
                        widePath(arena, path),
                        securityAttributes.attributes());
            requireTrue(result, "CreateDirectoryW");
          } finally {
            securityAttributes.close();
          }
        }
      }
    }

    /** Validates that a native control handle remains a non-reparse owner-only object. */
    private static final class ControlSecurity {
      private static void validateRegularOwnerOnlyControl(NativeHandle handle, CurrentOwner owner)
          throws IOException {
        validateExpectedKind(handle, false);
        validateOwnerOnlySecurity(handle, owner);
      }

      private static void validateDirectoryOwnerOnlyControl(NativeHandle handle, CurrentOwner owner)
          throws IOException {
        validateExpectedKind(handle, true);
        validateOwnerOnlySecurity(handle, owner);
      }

      private static void validateRegularNonReparse(NativeHandle handle) throws IOException {
        validateExpectedKind(handle, false);
      }

      private static void validateExpectedKind(NativeHandle handle, boolean directory)
          throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment attributes = arena.allocate(8L, Integer.BYTES);
          NativeResult<Integer> result =
              bindings()
                  .invokeInt(
                      bindings().getFileInformationByHandleEx,
                      handle.segment(),
                      FILE_ATTRIBUTE_TAG_INFO,
                      attributes,
                      8);
          requireTrue(result, "GetFileInformationByHandleEx(FileAttributeTagInfo)");
          int flags = attributes.get(ValueLayout.JAVA_INT, 0L);
          boolean actualDirectory = (flags & FILE_ATTRIBUTE_DIRECTORY) != 0;
          if ((flags & FILE_ATTRIBUTE_REPARSE_POINT) != 0 || actualDirectory != directory) {
            throw new IOException(
                "FinGrind Windows coordination state must remain an exact non-reparse "
                    + (directory ? "directory" : "regular file")
                    + " handle.");
          }
        }
      }

      private static void validateOwnerOnlySecurity(NativeHandle handle, CurrentOwner owner)
          throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment ownerOut = arena.allocate(ValueLayout.ADDRESS);
          MemorySegment daclOut = arena.allocate(ValueLayout.ADDRESS);
          MemorySegment securityDescriptorOut = arena.allocate(ValueLayout.ADDRESS);
          NativeResult<Integer> result =
              bindings()
                  .invokeDirectInt(
                      bindings().getSecurityInfo,
                      handle.segment(),
                      SE_FILE_OBJECT,
                      OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
                      ownerOut,
                      MemorySegment.NULL,
                      daclOut,
                      MemorySegment.NULL,
                      securityDescriptorOut);
          if (result.value() != 0) {
            throw new IOException(
                "GetSecurityInfo failed with Windows error "
                    + Integer.toUnsignedString(result.value())
                    + ".");
          }
          long securityDescriptorBits =
              securityDescriptorOut.get(ValueLayout.ADDRESS, 0L).address();
          if (securityDescriptorBits == 0L) {
            throw new IOException("GetSecurityInfo did not return a security descriptor.");
          }
          try {
            MemorySegment expectedOwnerSid = owner.ownerSid();
            MemorySegment actualOwnerSid = ownerOut.get(ValueLayout.ADDRESS, 0L);
            MemorySegment dacl = daclOut.get(ValueLayout.ADDRESS, 0L);
            if (actualOwnerSid.address() == 0L || dacl.address() == 0L) {
              throw new IOException(
                  "FinGrind coordination state is missing an explicit owner-only DACL.");
            }
            requireTrue(
                bindings().invokeInt(bindings().equalSid, actualOwnerSid, expectedOwnerSid),
                "EqualSid(owner)");

            MemorySegment control = arena.allocate(ValueLayout.JAVA_SHORT);
            MemorySegment revision = arena.allocate(ValueLayout.JAVA_INT);
            requireTrue(
                bindings()
                    .invokeInt(
                        bindings().getSecurityDescriptorControl,
                        MemorySegment.ofAddress(securityDescriptorBits),
                        control,
                        revision),
                "GetSecurityDescriptorControl");
            if ((control.get(ValueLayout.JAVA_SHORT, 0L) & SE_DACL_PROTECTED) == 0) {
              throw new IOException("FinGrind coordination state must retain a protected DACL.");
            }

            MemorySegment present = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment daclFromDescriptor = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment defaulted = arena.allocate(ValueLayout.JAVA_INT);
            requireTrue(
                bindings()
                    .invokeInt(
                        bindings().getSecurityDescriptorDacl,
                        MemorySegment.ofAddress(securityDescriptorBits),
                        present,
                        daclFromDescriptor,
                        defaulted),
                "GetSecurityDescriptorDacl");
            MemorySegment exactDacl = daclFromDescriptor.get(ValueLayout.ADDRESS, 0L);
            if (present.get(ValueLayout.JAVA_INT, 0L) == 0 || exactDacl.address() == 0L) {
              throw new IOException(
                  "FinGrind coordination state must retain one explicit non-null DACL.");
            }
            requireExactOwnerOnlyAce(exactDacl, expectedOwnerSid, arena);
          } finally {
            localFree(MemorySegment.ofAddress(securityDescriptorBits));
          }
        }
      }

      private static void requireExactOwnerOnlyAce(
          MemorySegment dacl, MemorySegment expectedOwnerSid, Arena arena) throws IOException {
        MemorySegment aclSizeInformation = arena.allocate(12L, Integer.BYTES);
        requireTrue(
            bindings()
                .invokeInt(
                    bindings().getAclInformation,
                    dacl,
                    aclSizeInformation,
                    12,
                    ACL_SIZE_INFORMATION),
            "GetAclInformation");
        if (aclSizeInformation.get(ValueLayout.JAVA_INT, 0L) != 1) {
          throw new IOException("FinGrind coordination state must retain exactly one owner ACE.");
        }
        MemorySegment aceOut = arena.allocate(ValueLayout.ADDRESS);
        requireTrue(bindings().invokeInt(bindings().getAce, dacl, 0, aceOut), "GetAce");
        MemorySegment ace = aceOut.get(ValueLayout.ADDRESS, 0L);
        if (ace.address() == 0L) {
          throw new IOException("FinGrind coordination state returned an empty owner ACE pointer.");
        }
        MemorySegment aceHeader = ace.reinterpret(8L);
        int type = Byte.toUnsignedInt(aceHeader.get(ValueLayout.JAVA_BYTE, 0L));
        int flags = Byte.toUnsignedInt(aceHeader.get(ValueLayout.JAVA_BYTE, 1L));
        int size = Short.toUnsignedInt(aceHeader.get(ValueLayout.JAVA_SHORT, 2L));
        int mask = aceHeader.get(ValueLayout.JAVA_INT, 4L);
        int ownerSidLength =
            bindings().invokeInt(bindings().getLengthSid, expectedOwnerSid).value();
        if (ownerSidLength <= 0
            || type != ACCESS_ALLOWED_ACE_TYPE
            || flags != 0
            || size != 8 + ownerSidLength
            || mask != FILE_ALL_ACCESS) {
          throw new IOException("FinGrind coordination state has an unexpected owner ACE shape.");
        }
        requireTrue(
            bindings()
                .invokeInt(
                    bindings().equalSid,
                    MemorySegment.ofAddress(ace.address() + 8L),
                    expectedOwnerSid),
            "EqualSid(owner ACE)");
      }
    }

    /** Reads and writes the immutable native control-file header through its exact handle. */
    private static final class ControlContent {
      private static void writeExact(NativeHandle handle, byte[] magic) throws IOException {
        requireFileSize(handle, 0L);
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment bytes = arena.allocateFrom(ValueLayout.JAVA_BYTE, magic);
          MemorySegment written = arena.allocate(ValueLayout.JAVA_INT);
          NativeResult<Integer> write =
              bindings()
                  .invokeInt(
                      bindings().writeFile,
                      handle.segment(),
                      bytes,
                      magic.length,
                      written,
                      MemorySegment.NULL);
          requireTrue(write, "WriteFile");
          if (written.get(ValueLayout.JAVA_INT, 0L) != magic.length) {
            throw new IOException(
                "WriteFile did not write the complete FinGrind coordination header.");
          }
        }
        requireTrue(
            bindings().invokeInt(bindings().flushFileBuffers, handle.segment()),
            "FlushFileBuffers");
        requireExactMagic(handle, magic);
      }

      private static void requireExactMagic(NativeHandle handle, byte[] magic) throws IOException {
        requireFileSize(handle, magic.length);
        setFilePointerToStart(handle);
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment actual = arena.allocate(magic.length, Byte.BYTES);
          MemorySegment read = arena.allocate(ValueLayout.JAVA_INT);
          NativeResult<Integer> result =
              bindings()
                  .invokeInt(
                      bindings().readFile,
                      handle.segment(),
                      actual,
                      magic.length,
                      read,
                      MemorySegment.NULL);
          requireTrue(result, "ReadFile");
          if (read.get(ValueLayout.JAVA_INT, 0L) != magic.length
              || !Arrays.equals(actual.toArray(ValueLayout.JAVA_BYTE), magic)) {
            throw new IOException("FinGrind coordination control-file magic is invalid.");
          }
        }
      }

      private static void requireFileSize(NativeHandle handle, long expectedSize)
          throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment size = arena.allocate(ValueLayout.JAVA_LONG);
          requireTrue(
              bindings().invokeInt(bindings().getFileSizeEx, handle.segment(), size),
              "GetFileSizeEx");
          if (size.get(ValueLayout.JAVA_LONG, 0L) != expectedSize) {
            throw new IOException(
                "FinGrind coordination control-file magic has an unexpected size.");
          }
        }
      }

      private static void setFilePointerToStart(NativeHandle handle) throws IOException {
        requireTrue(
            bindings()
                .invokeInt(
                    bindings().setFilePointerEx,
                    handle.segment(),
                    0L,
                    MemorySegment.NULL,
                    FILE_BEGIN),
            "SetFilePointerEx");
      }
    }

    private static boolean tryLock(NativeHandle handle, long position, long size)
        throws IOException {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment overlapped = zeroedOverlapped(arena, position);
        NativeResult<Integer> result =
            bindings()
                .invokeInt(
                    bindings().lockFileEx,
                    handle.segment(),
                    LOCKFILE_EXCLUSIVE_LOCK | LOCKFILE_FAIL_IMMEDIATELY,
                    0,
                    lowDword(size),
                    highDword(size),
                    overlapped);
        if (result.value() != 0) {
          return true;
        }
        if (result.lastError() == ERROR_LOCK_VIOLATION) {
          return false;
        }
        throw windowsFailure("LockFileEx", result.lastError());
      }
    }

    private static void unlock(NativeHandle handle, long position, long size) throws IOException {
      try (Arena arena = Arena.ofConfined()) {
        requireTrue(
            bindings()
                .invokeInt(
                    bindings().unlockFileEx,
                    handle.segment(),
                    0,
                    lowDword(size),
                    highDword(size),
                    zeroedOverlapped(arena, position)),
            "UnlockFileEx");
      }
    }

    private static MemorySegment zeroedOverlapped(Arena arena, long position) {
      long pointerSize = ValueLayout.ADDRESS.byteSize();
      long eventOffset = alignUp(2L * pointerSize + 8L, pointerSize);
      MemorySegment overlapped =
          arena.allocate(eventOffset + pointerSize, pointerSize).fill((byte) 0);
      overlapped.set(ValueLayout.JAVA_INT, 2L * pointerSize, lowDword(position));
      overlapped.set(ValueLayout.JAVA_INT, 2L * pointerSize + Integer.BYTES, highDword(position));
      return overlapped;
    }

    private static int lowDword(long value) {
      return (int) value;
    }

    private static int highDword(long value) {
      return (int) (value >>> Integer.SIZE);
    }

    private static long alignUp(long value, long alignment) {
      return (value + alignment - 1L) & -alignment;
    }

    private static MemorySegment widePath(Arena arena, Path path) {
      byte[] encoded =
          OwnerSecurityCodecs.extendedWindowsPath(path).getBytes(StandardCharsets.UTF_16LE);
      MemorySegment result = arena.allocate(encoded.length + Character.BYTES, Character.BYTES);
      result.asByteBuffer().put(encoded).put((byte) 0).put((byte) 0);
      return result;
    }

    private static void closeHandle(NativeHandle handle) throws IOException {
      requireTrue(bindings().invokeInt(bindings().closeHandle, handle.segment()), "CloseHandle");
    }

    private static void closeHandlePreservingFailure(
        NativeHandle handle, Throwable primaryFailure) {
      try {
        closeHandle(handle);
      } catch (IOException closeFailure) {
        primaryFailure.addSuppressed(closeFailure);
      }
    }

    private static void localFree(MemorySegment allocation) throws IOException {
      NativeResult<Long> result = bindings().invokeAddress(bindings().localFree, allocation);
      if (result.value() != 0L) {
        throw windowsFailure("LocalFree", result.lastError());
      }
    }

    private static void requireTrue(NativeResult<Integer> result, String operation)
        throws IOException {
      if (result.value() == 0) {
        throw windowsFailure(operation, result.lastError());
      }
    }

    private static WindowsFailure windowsFailure(String operation, int lastError) {
      return new WindowsFailure(operation, lastError);
    }

    private static Bindings bindings() throws IOException {
      BINDING_LOCK.lock();
      try {
        @Nullable Bindings existing = BINDINGS;
        if (existing == null) {
          SqliteNativeAccessGate.requireEnabled();
          existing = Bindings.bind();
          BINDINGS = existing;
        }
        return existing;
      } finally {
        BINDING_LOCK.unlock();
      }
    }

    private record NativeHandle(long bits, boolean created) {
      private NativeHandle {
        if (bits == 0L || bits == -1L) {
          throw new IllegalArgumentException(
              "Windows coordination handles must be valid native handles.");
        }
      }

      private MemorySegment segment() {
        return MemorySegment.ofAddress(bits);
      }
    }

    private record NativeResult<T>(T value, int lastError) {}

    /** JVM-local overlap proof for Windows, where LockFileEx does not promise self-conflict. */
    private static final class ProcessRangeLease {
      private final String controlPath;
      private final long position;
      private final long size;
      private boolean closed;

      private ProcessRangeLease(String controlPath, long position, long size) {
        this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
        this.position = position;
        this.size = size;
      }

      private static @Nullable ProcessRangeLease tryAcquire(
          Path controlPath, long position, long size) {
        String normalizedControlPath =
            Objects.requireNonNull(controlPath, "controlPath")
                .toAbsolutePath()
                .normalize()
                .toString();
        PROCESS_RANGE_LOCK.lock();
        try {
          List<ProcessRangeLease> heldRanges =
              PROCESS_RANGES.computeIfAbsent(normalizedControlPath, ignored -> new ArrayList<>());
          for (ProcessRangeLease heldRange : heldRanges) {
            if (rangesOverlap(position, size, heldRange.position, heldRange.size)) {
              if (heldRanges.isEmpty()) {
                PROCESS_RANGES.remove(normalizedControlPath, heldRanges);
              }
              return null;
            }
          }
          ProcessRangeLease acquired = new ProcessRangeLease(normalizedControlPath, position, size);
          heldRanges.add(acquired);
          return acquired;
        } finally {
          PROCESS_RANGE_LOCK.unlock();
        }
      }

      void close() {
        if (closed) {
          return;
        }
        closed = true;
        PROCESS_RANGE_LOCK.lock();
        try {
          List<ProcessRangeLease> heldRanges = PROCESS_RANGES.get(controlPath);
          if (heldRanges == null || !heldRanges.remove(this)) {
            throw new IllegalStateException(
                "The FinGrind Windows coordination-range ownership changed unexpectedly.");
          }
          if (heldRanges.isEmpty()) {
            PROCESS_RANGES.remove(controlPath, heldRanges);
          }
        } finally {
          PROCESS_RANGE_LOCK.unlock();
        }
      }

      private static boolean rangesOverlap(
          long firstPosition, long firstSize, long secondPosition, long secondSize) {
        long firstEnd = firstPosition + firstSize;
        long secondEnd = secondPosition + secondSize;
        return firstPosition < secondEnd && secondPosition < firstEnd;
      }
    }

    /** Native Windows error returned by one exact coordination API call. */
    private static final class WindowsFailure extends IOException {
      private static final long serialVersionUID = 1L;
      private final int lastError;

      private WindowsFailure(String operation, int lastError) {
        super(
            Objects.requireNonNull(operation, "operation")
                + " failed with Windows error "
                + Integer.toUnsignedString(lastError)
                + ".");
        this.lastError = lastError;
      }

      private boolean hasError(int expected) {
        return lastError == expected;
      }
    }

    /** Retains the process token's default object-owner SID while a control object is admitted. */
    private static final class CurrentOwner implements AutoCloseable {
      private final Arena arena;
      private final NativeHandle token;
      private final MemorySegment ownerSid;
      private final String ownerSidText;
      private boolean closed;

      private CurrentOwner(
          Arena arena, NativeHandle token, MemorySegment ownerSid, String ownerSidText) {
        this.arena = Objects.requireNonNull(arena, "arena");
        this.token = Objects.requireNonNull(token, "token");
        this.ownerSid = Objects.requireNonNull(ownerSid, "ownerSid");
        this.ownerSidText = Objects.requireNonNull(ownerSidText, "ownerSidText");
      }

      private static CurrentOwner acquire() throws IOException {
        Arena arena = Arena.ofConfined();
        try {
          MemorySegment tokenOut = arena.allocate(ValueLayout.ADDRESS);
          NativeResult<Long> currentProcess =
              bindings().invokeAddress(bindings().getCurrentProcess);
          NativeResult<Integer> openToken =
              bindings()
                  .invokeInt(
                      bindings().openProcessToken,
                      MemorySegment.ofAddress(currentProcess.value()),
                      TOKEN_QUERY,
                      tokenOut);
          requireTrue(openToken, "OpenProcessToken");
          NativeHandle token =
              new NativeHandle(tokenOut.get(ValueLayout.ADDRESS, 0L).address(), false);
          try {
            MemorySegment ownerSid = readTokenOwnerSid(arena, token);
            return new CurrentOwner(arena, token, ownerSid, OwnerSecurityCodecs.sidText(ownerSid));
          } catch (IOException | RuntimeException failure) {
            closeHandlePreservingFailure(token, failure);
            throw failure;
          }
        } catch (IOException | RuntimeException failure) {
          arena.close();
          throw failure;
        }
      }

      private static MemorySegment readTokenOwnerSid(Arena arena, NativeHandle token)
          throws IOException {
        MemorySegment requiredSize = arena.allocate(ValueLayout.JAVA_INT);
        NativeResult<Integer> initial =
            bindings()
                .invokeInt(
                    bindings().getTokenInformation,
                    token.segment(),
                    TOKEN_OWNER,
                    MemorySegment.NULL,
                    0,
                    requiredSize);
        if (initial.value() != 0 || initial.lastError() != ERROR_INSUFFICIENT_BUFFER) {
          throw windowsFailure("GetTokenInformation(TokenOwner)", initial.lastError());
        }
        int byteCount = requiredSize.get(ValueLayout.JAVA_INT, 0L);
        if (byteCount < ValueLayout.ADDRESS.byteSize()) {
          throw new IOException("GetTokenInformation(TokenOwner) returned an invalid owner size.");
        }
        MemorySegment tokenOwner = arena.allocate(byteCount, ValueLayout.ADDRESS.byteAlignment());
        requireTrue(
            bindings()
                .invokeInt(
                    bindings().getTokenInformation,
                    token.segment(),
                    TOKEN_OWNER,
                    tokenOwner,
                    byteCount,
                    requiredSize),
            "GetTokenInformation(TokenOwner)");
        MemorySegment ownerSid = tokenOwner.get(ValueLayout.ADDRESS, 0L);
        if (ownerSid.address() == 0L) {
          throw new IOException("GetTokenInformation(TokenOwner) returned no owner SID.");
        }
        return ownerSid;
      }

      private MemorySegment ownerSid() {
        return ownerSid;
      }

      private SecurityAttributes securityAttributes(Arena callArena) throws IOException {
        MemorySegment securityDescriptor = OwnerSecurityCodecs.securityDescriptor(ownerSidText);
        try {
          long pointerSize = ValueLayout.ADDRESS.byteSize();
          long descriptorOffset = alignUp(Integer.BYTES, pointerSize);
          long inheritOffset = descriptorOffset + pointerSize;
          long structureSize = alignUp(inheritOffset + Integer.BYTES, pointerSize);
          MemorySegment attributes = callArena.allocate(structureSize, pointerSize).fill((byte) 0);
          attributes.set(ValueLayout.JAVA_INT, 0L, Math.toIntExact(structureSize));
          attributes.set(ValueLayout.ADDRESS, descriptorOffset, securityDescriptor);
          attributes.set(ValueLayout.JAVA_INT, inheritOffset, 0);
          return new SecurityAttributes(attributes, securityDescriptor);
        } catch (RuntimeException | Error failure) {
          try {
            localFree(securityDescriptor);
          } catch (IOException freeFailure) {
            failure.addSuppressed(freeFailure);
          }
          throw failure;
        }
      }

      @Override
      public void close() throws IOException {
        if (closed) {
          return;
        }
        closed = true;
        IOException failure = null;
        try {
          closeHandle(token);
        } catch (IOException exception) {
          failure = exception;
        }
        arena.close();
        if (failure != null) {
          throw failure;
        }
      }
    }

    /** One call-local SECURITY_ATTRIBUTES record and its LocalAlloc-owned descriptor. */
    private record SecurityAttributes(MemorySegment attributes, MemorySegment securityDescriptor) {
      private SecurityAttributes {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(securityDescriptor, "securityDescriptor");
      }

      void close() throws IOException {
        localFree(securityDescriptor);
      }
    }

    /** Encodes the account-owner SID into the exact Windows security descriptor representation. */
    private static final class OwnerSecurityCodecs {
      private static String sidText(MemorySegment sid) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment textOut = arena.allocate(ValueLayout.ADDRESS);
          requireTrue(
              bindings().invokeInt(bindings().convertSidToStringSidW, sid, textOut),
              "ConvertSidToStringSidW");
          MemorySegment text = textOut.get(ValueLayout.ADDRESS, 0L);
          if (text.address() == 0L) {
            throw new IOException("ConvertSidToStringSidW returned no SID string.");
          }
          try {
            return readWideString(text);
          } finally {
            localFree(text);
          }
        }
      }

      private static MemorySegment securityDescriptor(String sidText) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          String sddl =
              "O:" + Objects.requireNonNull(sidText, "sidText") + "D:P(A;;FA;;;" + sidText + ")";
          MemorySegment descriptorOut = arena.allocate(ValueLayout.ADDRESS);
          MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_INT);
          requireTrue(
              bindings()
                  .invokeInt(
                      bindings().convertStringSecurityDescriptorToSecurityDescriptorW,
                      wideString(arena, sddl),
                      SDDL_REVISION_1,
                      descriptorOut,
                      sizeOut),
              "ConvertStringSecurityDescriptorToSecurityDescriptorW");
          MemorySegment descriptor = descriptorOut.get(ValueLayout.ADDRESS, 0L);
          if (descriptor.address() == 0L) {
            throw new IOException(
                "ConvertStringSecurityDescriptorToSecurityDescriptorW returned no descriptor.");
          }
          return MemorySegment.ofAddress(descriptor.address());
        }
      }

      private static String readWideString(MemorySegment pointer) throws IOException {
        byte[] bytes =
            MemorySegment.ofAddress(Objects.requireNonNull(pointer, "pointer").address())
                .reinterpret(MAXIMUM_SID_STRING_BYTES)
                .toArray(ValueLayout.JAVA_BYTE);
        int length = 0;
        while (length + 1 < bytes.length && (bytes[length] != 0 || bytes[length + 1] != 0)) {
          length += Character.BYTES;
        }
        if (length + 1 >= bytes.length) {
          throw new IOException(
              "Windows owner SID text exceeded the bounded coordination transport buffer.");
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_16LE);
      }

      private static MemorySegment wideString(Arena arena, String value) {
        byte[] encoded = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment result = arena.allocate(encoded.length + Character.BYTES, Character.BYTES);
        result.asByteBuffer().put(encoded).put((byte) 0).put((byte) 0);
        return result;
      }

      private static String extendedWindowsPath(Path path) {
        String normalized =
            Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
        if (normalized.startsWith("\\\\?\\")) {
          return normalized;
        }
        if (normalized.startsWith("\\\\")) {
          return "\\\\?\\UNC\\" + normalized.substring(2);
        }
        return "\\\\?\\" + normalized;
      }
    }

    /** Bound Win32 calls; every BOOL/HANDLE call captures GetLastError at the call boundary. */
    private static final class Bindings {
      private static final MemoryLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();

      private final MethodHandle createFileW;
      private final MethodHandle readFile;
      private final MethodHandle writeFile;
      private final MethodHandle flushFileBuffers;
      private final MethodHandle lockFileEx;
      private final MethodHandle unlockFileEx;
      private final MethodHandle closeHandle;
      private final MethodHandle getFileInformationByHandleEx;
      private final MethodHandle createDirectoryW;
      private final MethodHandle getFileSizeEx;
      private final MethodHandle setFilePointerEx;
      private final MethodHandle getCurrentProcess;
      private final MethodHandle openProcessToken;
      private final MethodHandle getTokenInformation;
      private final MethodHandle localFree;
      private final MethodHandle convertSidToStringSidW;
      private final MethodHandle convertStringSecurityDescriptorToSecurityDescriptorW;
      private final MethodHandle getSecurityInfo;
      private final MethodHandle getSecurityDescriptorControl;
      private final MethodHandle getSecurityDescriptorDacl;
      private final MethodHandle getAclInformation;
      private final MethodHandle getAce;
      private final MethodHandle getLengthSid;
      private final MethodHandle equalSid;

      private Bindings(
          MethodHandle createFileW,
          MethodHandle readFile,
          MethodHandle writeFile,
          MethodHandle flushFileBuffers,
          MethodHandle lockFileEx,
          MethodHandle unlockFileEx,
          MethodHandle closeHandle,
          MethodHandle getFileInformationByHandleEx,
          MethodHandle createDirectoryW,
          MethodHandle getFileSizeEx,
          MethodHandle setFilePointerEx,
          MethodHandle getCurrentProcess,
          MethodHandle openProcessToken,
          MethodHandle getTokenInformation,
          MethodHandle localFree,
          MethodHandle convertSidToStringSidW,
          MethodHandle convertStringSecurityDescriptorToSecurityDescriptorW,
          MethodHandle getSecurityInfo,
          MethodHandle getSecurityDescriptorControl,
          MethodHandle getSecurityDescriptorDacl,
          MethodHandle getAclInformation,
          MethodHandle getAce,
          MethodHandle getLengthSid,
          MethodHandle equalSid) {
        this.createFileW = createFileW;
        this.readFile = readFile;
        this.writeFile = writeFile;
        this.flushFileBuffers = flushFileBuffers;
        this.lockFileEx = lockFileEx;
        this.unlockFileEx = unlockFileEx;
        this.closeHandle = closeHandle;
        this.getFileInformationByHandleEx = getFileInformationByHandleEx;
        this.createDirectoryW = createDirectoryW;
        this.getFileSizeEx = getFileSizeEx;
        this.setFilePointerEx = setFilePointerEx;
        this.getCurrentProcess = getCurrentProcess;
        this.openProcessToken = openProcessToken;
        this.getTokenInformation = getTokenInformation;
        this.localFree = localFree;
        this.convertSidToStringSidW = convertSidToStringSidW;
        this.convertStringSecurityDescriptorToSecurityDescriptorW =
            convertStringSecurityDescriptorToSecurityDescriptorW;
        this.getSecurityInfo = getSecurityInfo;
        this.getSecurityDescriptorControl = getSecurityDescriptorControl;
        this.getSecurityDescriptorDacl = getSecurityDescriptorDacl;
        this.getAclInformation = getAclInformation;
        this.getAce = getAce;
        this.getLengthSid = getLengthSid;
        this.equalSid = equalSid;
      }

      private static Bindings bind() throws IOException {
        SymbolLookup kernel32 = libraryLookup("kernel32");
        SymbolLookup advapi32 = libraryLookup("advapi32");
        return new Bindings(
            captured(
                kernel32,
                "CreateFileW",
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "ReadFile",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "WriteFile",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "FlushFileBuffers",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "LockFileEx",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "UnlockFileEx",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "CloseHandle",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "GetFileInformationByHandleEx",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT)),
            captured(
                kernel32,
                "CreateDirectoryW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "GetFileSizeEx",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "SetFilePointerEx",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT)),
            captured(kernel32, "GetCurrentProcess", FunctionDescriptor.of(ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "OpenProcessToken",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "GetTokenInformation",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                kernel32,
                "LocalFree",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "ConvertSidToStringSidW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "ConvertStringSecurityDescriptorToSecurityDescriptorW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            direct(
                advapi32,
                "GetSecurityInfo",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "GetSecurityDescriptorControl",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "GetSecurityDescriptorDacl",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "GetAclInformation",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT)),
            captured(
                advapi32,
                "GetAce",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "GetLengthSid",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
            captured(
                advapi32,
                "EqualSid",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));
      }

      private NativeResult<Integer> invokeInt(MethodHandle handle, Object... arguments)
          throws IOException {
        NativeResult<Object> result = invoke(handle, arguments);
        return new NativeResult<>((int) result.value(), result.lastError());
      }

      private NativeResult<Long> invokeAddress(MethodHandle handle, Object... arguments)
          throws IOException {
        NativeResult<Object> result = invoke(handle, arguments);
        return new NativeResult<>(((MemorySegment) result.value()).address(), result.lastError());
      }

      private NativeResult<Object> invoke(MethodHandle handle, Object... arguments)
          throws IOException {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment callState = arena.allocate(CAPTURE_STATE_LAYOUT);
          Object[] capturedArguments = new Object[arguments.length + 1];
          capturedArguments[0] = callState;
          System.arraycopy(arguments, 0, capturedArguments, 1, arguments.length);
          Object value = handle.invokeWithArguments(capturedArguments);
          return new NativeResult<>(value, callState.get(ValueLayout.JAVA_INT, 0L));
        } catch (RuntimeException | Error exception) {
          throw exception;
        } catch (Throwable exception) {
          throw new IOException(
              "FinGrind could not invoke one Windows coordination operation.", exception);
        }
      }

      private NativeResult<Integer> invokeDirectInt(MethodHandle handle, Object... arguments)
          throws IOException {
        try {
          return new NativeResult<>((int) handle.invokeWithArguments(arguments), 0);
        } catch (RuntimeException | Error exception) {
          throw exception;
        } catch (Throwable exception) {
          throw new IOException(
              "FinGrind could not invoke one Windows coordination operation.", exception);
        }
      }

      private static SymbolLookup libraryLookup(String libraryName) throws IOException {
        try {
          return SymbolLookup.libraryLookup(
              Objects.requireNonNull(libraryName, "libraryName"), Arena.global());
        } catch (IllegalArgumentException exception) {
          throw new IOException(
              "FinGrind could not load Windows native library " + libraryName + ".", exception);
        }
      }

      private static MethodHandle captured(
          SymbolLookup lookup, String symbolName, FunctionDescriptor descriptor)
          throws IOException {
        return Linker.nativeLinker()
            .downcallHandle(
                requiredSymbol(lookup, symbolName),
                descriptor,
                Linker.Option.captureCallState("GetLastError"));
      }

      private static MethodHandle direct(
          SymbolLookup lookup, String symbolName, FunctionDescriptor descriptor)
          throws IOException {
        return Linker.nativeLinker().downcallHandle(requiredSymbol(lookup, symbolName), descriptor);
      }

      private static MemorySegment requiredSymbol(SymbolLookup lookup, String symbolName)
          throws IOException {
        Optional<MemorySegment> symbol =
            Objects.requireNonNull(lookup, "lookup")
                .find(Objects.requireNonNull(symbolName, "symbolName"));
        if (symbol.isEmpty()) {
          throw new IOException(
              "Windows coordination transport is missing native symbol " + symbolName + ".");
        }
        return symbol.orElseThrow();
      }
    }
  }
}
