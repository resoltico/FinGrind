package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Connects the generic Windows private-file protocol to the narrowly scoped Win32 ABI helpers. */
final class WindowsPrivateOutputFileFfmTransport {
  private WindowsPrivateOutputFileFfmTransport() {}

  static WindowsPrivateOutputFileTransport.NativeFileOperations operationsFor(
      WindowsPrivateOutputFileCalls calls) {
    return new FfmOperations(calls);
  }

  static String protectedOwnerOnlyDescriptor(String ownerSidText) {
    return WindowsPrivateOutputFileOwner.protectedOwnerOnlyDescriptor(ownerSidText);
  }

  /** Production native-file operations backed by the Win32 FFM call table. */
  private static final class FfmOperations
      implements WindowsPrivateOutputFileTransport.NativeFileOperations {
    private final WindowsPrivateOutputFileCalls calls;

    private FfmOperations(WindowsPrivateOutputFileCalls calls) {
      this.calls = Objects.requireNonNull(calls, "calls");
    }

    @Override
    public WindowsPrivateOutputFileTransport.CurrentOwner acquireCurrentOwner() throws IOException {
      return WindowsPrivateOutputFileOwner.acquire(calls);
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile createNew(
        Path file, WindowsPrivateOutputFileTransport.CurrentOwner owner) throws IOException {
      return createProtectedFile(Objects.requireNonNull(file, "file"), requireOwner(owner));
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile openExisting(
        Path file,
        PrivateOutputFile.Access access,
        WindowsPrivateOutputFileTransport.CurrentOwner owner)
        throws IOException {
      requireOwner(owner);
      return openExistingFile(
          Objects.requireNonNull(file, "file"),
          desiredAccess(Objects.requireNonNull(access, "access")));
    }

    /**
     * Creates a protected file and closes its new handle if descriptor cleanup prevents handoff.
     */
    private WindowsPrivateOutputFileHandle createProtectedFile(
        Path file, WindowsPrivateOutputFileOwner owner) throws IOException {
      WindowsPrivateOutputFileHandle opened = null;
      try (Arena arena = Arena.ofConfined();
          WindowsPrivateOutputFileOwner.SecurityAttributes attributes =
              owner.securityAttributes(arena)) {
        opened =
            createdHandle(
                createFile(
                    arena,
                    file,
                    WindowsPrivateOutputFileNative.GENERIC_READ
                        | WindowsPrivateOutputFileNative.GENERIC_WRITE,
                    attributes.attributes(),
                    WindowsPrivateOutputFileNative.CREATE_NEW),
                file);
        return opened;
      } catch (IOException | RuntimeException | Error failure) {
        if (opened != null) {
          opened.closePreservingFailure(failure);
        }
        throw failure;
      }
    }

    private WindowsPrivateOutputFileHandle openExistingFile(Path file, int desiredAccess)
        throws IOException {
      try (Arena arena = Arena.ofConfined()) {
        return openedHandle(
            createFile(
                arena,
                file,
                desiredAccess,
                MemorySegment.NULL,
                WindowsPrivateOutputFileNative.OPEN_EXISTING));
      }
    }

    private WindowsPrivateOutputFileNative.Result<Long> createFile(
        Arena arena,
        Path file,
        int desiredAccess,
        MemorySegment securityAttributes,
        int creationDisposition)
        throws IOException {
      return calls
          .fileCalls()
          .createFileW(
              WindowsPrivateOutputFileNative.extendedWidePath(arena, file),
              desiredAccess,
              WindowsPrivateOutputFileNative.FILE_SHARE_READ_WRITE,
              securityAttributes,
              creationDisposition,
              WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_NORMAL
                  | WindowsPrivateOutputFileNative.FILE_FLAG_OPEN_REPARSE_POINT,
              MemorySegment.NULL);
    }

    private WindowsPrivateOutputFileHandle createdHandle(
        WindowsPrivateOutputFileNative.Result<Long> result, Path file) throws IOException {
      if (result.value() == -1L) {
        if (result.lastError() == WindowsPrivateOutputFileNative.ERROR_FILE_EXISTS
            || result.lastError() == WindowsPrivateOutputFileNative.ERROR_ALREADY_EXISTS) {
          throw new FileAlreadyExistsException(file.toString());
        }
        throw WindowsPrivateOutputFileNative.windowsFailure("CreateFileW", result.lastError());
      }
      return new WindowsPrivateOutputFileHandle(
          calls, new WindowsPrivateOutputFileNative.Handle(result.value()));
    }

    private WindowsPrivateOutputFileHandle openedHandle(
        WindowsPrivateOutputFileNative.Result<Long> result) throws IOException {
      if (result.value() == -1L) {
        throw WindowsPrivateOutputFileNative.windowsFailure("CreateFileW", result.lastError());
      }
      return new WindowsPrivateOutputFileHandle(
          calls, new WindowsPrivateOutputFileNative.Handle(result.value()));
    }

    private static int desiredAccess(PrivateOutputFile.Access access) {
      return access == PrivateOutputFile.Access.READ_ONLY
          ? WindowsPrivateOutputFileNative.GENERIC_READ
          : WindowsPrivateOutputFileNative.GENERIC_READ
              | WindowsPrivateOutputFileNative.GENERIC_WRITE;
    }

    private static WindowsPrivateOutputFileOwner requireOwner(
        WindowsPrivateOutputFileTransport.CurrentOwner owner) {
      if (!(owner instanceof WindowsPrivateOutputFileOwner nativeOwner)) {
        throw new IllegalArgumentException(
            "The Windows FFM binding received an incompatible owner.");
      }
      return nativeOwner;
    }
  }
}
