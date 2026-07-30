package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Exercises the Windows FFM transport protocol against a complete deterministic Win32 model. */
class WindowsPrivateOutputFileFfmTransportTest {
  private static final Path PRIVATE_PATH = Path.of("synthetic-private-output.fg");

  @Test
  void retainedHandleRejectsInvalidNativeReadCountsBeforeMutatingTheDestination() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileHandle handle = windows.handle()) {
      windows.scenario.handle.readCount = -1;
      assertThrows(IOException.class, () -> handle.read(ByteBuffer.allocate(1)));

      windows.scenario.handle.readCount = 2;
      assertThrows(IOException.class, () -> handle.read(ByteBuffer.allocate(1)));
    }
  }

  @Test
  void preservesEachNativeHandleFailureAtItsCapabilityBoundary() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileHandle handle = windows.handle()) {
      windows.scenario.handle.readFileResult = new WindowsPrivateOutputFileNative.Result<>(0, 8);
      assertThrows(IOException.class, () -> handle.read(ByteBuffer.allocate(1)));
      windows.scenario.handle.readFileResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.writeFileResult = new WindowsPrivateOutputFileNative.Result<>(0, 9);
      assertThrows(IOException.class, () -> handle.write(ByteBuffer.wrap(new byte[] {1})));
      windows.scenario.handle.writeFileResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.fileSizeResult = new WindowsPrivateOutputFileNative.Result<>(0, 10);
      assertThrows(IOException.class, handle::size);
      windows.scenario.handle.fileSizeResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.filePointerResult =
          new WindowsPrivateOutputFileNative.Result<>(0, 11);
      assertThrows(IOException.class, () -> handle.position(1L));
      windows.scenario.handle.filePointerResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.endOfFileResult = new WindowsPrivateOutputFileNative.Result<>(0, 12);
      assertThrows(IOException.class, () -> handle.truncate(1L));
      windows.scenario.handle.endOfFileResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.flushResult = new WindowsPrivateOutputFileNative.Result<>(0, 13);
      assertThrows(IOException.class, handle::force);
      windows.scenario.handle.flushResult = SyntheticWin32.intResult(1);

      windows.scenario.handle.fileInformationResult =
          new WindowsPrivateOutputFileNative.Result<>(0, 14);
      assertThrows(IOException.class, handle::physicalObjectIdentity);
      assertThrows(IOException.class, windows::securityProof);
    }
  }

  @Test
  void retainedHandleRejectsInvalidTransfersAndRangeLockFailuresWithoutLeakingOwnership()
      throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileHandle handle = windows.handle()) {
      assertEquals(0, handle.read(ByteBuffer.allocate(0)));
      windows.scenario.handle.readCount = 2;
      windows.scenario.handle.readBytes = new byte[] {9, 8};
      ByteBuffer destination = ByteBuffer.allocate(2);
      assertEquals(2, handle.read(destination));
      assertEquals(ByteBuffer.wrap(new byte[] {9, 8}), destination.flip());

      windows.scenario.handle.readCount = 3;
      assertThrows(IOException.class, () -> handle.read(ByteBuffer.allocate(2)));
      assertEquals(0, handle.write(ByteBuffer.allocate(0)));
      windows.scenario.handle.writeCount = 0;
      assertThrows(IOException.class, () -> handle.write(ByteBuffer.wrap(new byte[] {1})));
      windows.scenario.handle.writeCount = 2;
      assertThrows(IOException.class, () -> handle.write(ByteBuffer.wrap(new byte[] {1})));
      windows.scenario.handle.writeCount = -1;
      assertThrows(IOException.class, () -> handle.write(ByteBuffer.wrap(new byte[] {1})));

      assertEquals(3L, handle.size());
      assertThrows(IllegalArgumentException.class, () -> handle.truncate(-1L));
      assertThrows(IllegalArgumentException.class, () -> handle.position(-1L));
      windows.scenario.handle.lockResult = new WindowsPrivateOutputFileNative.Result<>(0, 5);
      assertThrows(IOException.class, () -> handle.tryExclusiveLock(2L, 1L));
      windows.scenario.handle.lockResult = new WindowsPrivateOutputFileNative.Result<>(1, 0);
      try (PrivateOutputFile.HeldLock ignored =
          Objects.requireNonNull(handle.tryExclusiveLock(4L, 1L))) {
        assertNull(handle.tryExclusiveLock(4L, 1L));
      }
    }
  }

  @Test
  void retainedHandlePreservesUnlockAndCloseFailuresWithoutReleasingThePrimaryCause()
      throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      try (WindowsPrivateOutputFileHandle unlockFailureHandle = windows.handle();
          PrivateOutputFile.HeldLock retainedLock =
              Objects.requireNonNull(unlockFailureHandle.tryExclusiveLock(0L, 1L))) {
        windows.scenario.handle.unlockResult = new WindowsPrivateOutputFileNative.Result<>(0, 6);

        IOException unlockFailure = assertThrows(IOException.class, retainedLock::close);
        assertTrue(String.valueOf(unlockFailure.getMessage()).contains("6"));
      }

      try (WindowsPrivateOutputFileHandle closeFailureHandle = windows.handle()) {
        Objects.requireNonNull(closeFailureHandle.tryExclusiveLock(2L, 1L));
        windows.scenario.handle.closeResult = new WindowsPrivateOutputFileNative.Result<>(0, 7);
        IOException closeFailure = assertThrows(IOException.class, closeFailureHandle::close);
        assertTrue(String.valueOf(closeFailure.getMessage()).contains("6"));
        assertEquals(1, closeFailure.getSuppressed().length);
      }

      try (WindowsPrivateOutputFileHandle cleanupHandle = windows.handle()) {
        RuntimeException primary = new RuntimeException("primary");
        cleanupHandle.closePreservingFailure(primary);
        assertEquals(1, primary.getSuppressed().length);
      }
    }
  }

  @Test
  void securityProofReportsEveryIncompleteDescriptorFactWithoutChangingItsNativeShape()
      throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      windows.scenario.entry.kind = WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY;
      windows.scenario.entry.reparsePoint = true;
      windows.scenario.descriptor.actualOwnerPresent = false;
      windows.scenario.descriptor.protectedDacl = false;
      WindowsPrivateOutputFileTransport.SecurityProof basic = windows.securityProof();
      assertEquals(WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY, basic.entryKind());
      assertTrue(basic.reparsePoint());
      assertFalse(basic.ownerMatchesCurrentTokenUser());
      assertFalse(basic.protectedDacl());

      windows.scenario.descriptor.daclPresent = false;
      WindowsPrivateOutputFileTransport.SecurityProof absentDacl = windows.securityProof();
      assertFalse(absentDacl.explicitNonNullDacl());
      assertEquals(0, absentDacl.aceCount());

      windows.scenario.descriptor.daclPresent = true;
      windows.scenario.descriptor.exactDaclPresent = false;
      assertFalse(windows.securityProof().explicitNonNullDacl());
      windows.scenario.descriptor.exactDaclPresent = true;
      windows.scenario.descriptor.securityInfoDaclPresent = false;
      assertFalse(windows.securityProof().explicitNonNullDacl());
      windows.scenario.descriptor.securityInfoDaclPresent = true;
      windows.scenario.ace.count = 2;
      assertEquals(2, windows.securityProof().aceCount());
      windows.scenario.ace.count = 1;
      windows.scenario.ace.present = false;
      assertFalse(windows.securityProof().exactSingleOwnerFullControlAce());
    }
  }

  @Test
  void securityProofRejectsNativeDescriptorFailuresAndEverySingleAceMismatch() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      windows.scenario.descriptor.securityInfoResult = 9;
      assertThrows(IOException.class, windows::securityProof);
      windows.scenario.descriptor.securityInfoResult = 0;
      windows.scenario.descriptor.securityDescriptorPresent = false;
      assertThrows(IOException.class, windows::securityProof);
    }

    assertSingleAceMismatch(windows -> windows.scenario.ace.sidLength = 0);
    assertSingleAceMismatch(windows -> windows.scenario.ace.type = 1);
    assertSingleAceMismatch(windows -> windows.scenario.ace.flags = 1);
    assertSingleAceMismatch(windows -> windows.scenario.ace.size = 0);
    assertSingleAceMismatch(windows -> windows.scenario.ace.mask = 0);
    assertSingleAceMismatch(windows -> windows.scenario.ace.equalSid = false);
  }

  @Test
  void ownerLifecycleRejectsInvalidNativeEvidenceAndKeepsDescriptorAllocationOwnershipExact()
      throws Exception {
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.initialTokenInformationResult =
                new WindowsPrivateOutputFileNative.Result<>(1, 0));
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.initialTokenInformationResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 0));
    assertOwnerAcquisitionRejected(windows -> windows.scenario.owner.informationBytes = 1);
    assertOwnerAcquisitionRejected(windows -> windows.scenario.owner.sidPresent = false);
    assertOwnerAcquisitionRejected(windows -> windows.scenario.owner.sidTextPresent = false);
    assertOwnerAcquisitionRejected(windows -> windows.scenario.owner.sidTextTerminated = false);

    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls());
        Arena attributesArena = Arena.ofConfined()) {
      windows.scenario.owner.descriptorPresent = false;
      assertThrows(IOException.class, () -> owner.securityAttributes(attributesArena));
    }

    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls());
        Arena attributesArena = Arena.ofConfined();
        WindowsPrivateOutputFileOwner.SecurityAttributes attributes =
            owner.securityAttributes(attributesArena)) {
      assertEquals("S-1-5-21-42", owner.ownerSidText());
      assertNotEquals(0L, attributes.attributes().address());
    }
  }

  @Test
  void ownerLifecycleClosesOnceAndPreservesDescriptorCleanupFailure() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls())) {
      owner.close();
      owner.close();
      assertThrows(IllegalStateException.class, owner::ownerSidText);

      try (WindowsPrivateOutputFileOwner descriptorOwner =
              WindowsPrivateOutputFileOwner.acquire(windows.calls());
          Arena closedArena =
              new WindowsPrivateOutputFileCloseFailingArena(Arena.ofConfined(), false)) {
        closedArena.close();
        assertThrows(
            IllegalStateException.class, () -> descriptorOwner.securityAttributes(closedArena));
      }
    }

    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls());
        Arena closedArena =
            new WindowsPrivateOutputFileCloseFailingArena(Arena.ofConfined(), false)) {
      closedArena.close();
      windows.scenario.owner.localFreeResult = new WindowsPrivateOutputFileNative.Result<>(1L, 26);

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> owner.securityAttributes(closedArena));

      assertEquals(1, failure.getSuppressed().length);
    }

    try (SyntheticWin32 windows = new SyntheticWin32()) {
      windows.sidText.set(ValueLayout.JAVA_BYTE, 0L, (byte) 0);
      windows.sidText.set(ValueLayout.JAVA_BYTE, 1L, (byte) 1);
      windows.sidText.set(ValueLayout.JAVA_BYTE, 2L, (byte) 0);
      windows.sidText.set(ValueLayout.JAVA_BYTE, 3L, (byte) 0);
      try (WindowsPrivateOutputFileOwner owner =
          WindowsPrivateOutputFileOwner.acquire(windows.calls())) {
        assertFalse(owner.ownerSidText().isEmpty());
      }
    }
  }

  @Test
  void closesAnOpenedNativeHandleWhenDescriptorCleanupPreventsItsReturn() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls())) {
      WindowsPrivateOutputFileTransport.NativeFileOperations operations =
          WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls());
      windows.scenario.owner.localFreeResult = new WindowsPrivateOutputFileNative.Result<>(1L, 27);

      assertThrows(IOException.class, () -> operations.createNew(PRIVATE_PATH, owner));

      assertTrue(windows.operationNames().contains("closeHandle"));
    }
  }

  @Test
  void preservesDescriptorConstructionFailureBeforeAnyProtectedFileHandleExists() throws Exception {
    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls())) {
      WindowsPrivateOutputFileTransport.NativeFileOperations operations =
          WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls());
      windows.scenario.owner.descriptorConversionResult =
          new WindowsPrivateOutputFileNative.Result<>(0, 28);

      assertThrows(IOException.class, () -> operations.createNew(PRIVATE_PATH, owner));

      assertFalse(windows.operationNames().contains("createFileW"));
    }
  }

  @Test
  void failsClosedForEveryOwnerAndDescriptorInteropFailure() throws Exception {
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.openProcessTokenResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 15));
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.finalTokenInformationResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 16));
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.convertSidResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 17));
    assertOwnerAcquisitionRejected(
        windows ->
            windows.scenario.owner.localFreeResult =
                new WindowsPrivateOutputFileNative.Result<>(1L, 18));

    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls());
        Arena attributesArena = Arena.ofConfined()) {
      windows.scenario.owner.descriptorConversionResult =
          new WindowsPrivateOutputFileNative.Result<>(0, 19);
      assertThrows(IOException.class, () -> owner.securityAttributes(attributesArena));
    }

    try (SyntheticWin32 windows = new SyntheticWin32();
        WindowsPrivateOutputFileOwner owner =
            WindowsPrivateOutputFileOwner.acquire(windows.calls())) {
      windows.scenario.handle.closeResult = new WindowsPrivateOutputFileNative.Result<>(0, 20);
      assertThrows(IOException.class, owner::close);
      assertThrows(IllegalStateException.class, owner::ownerSid);
    }

    assertSecurityProofRejected(
        windows ->
            windows.scenario.securityResults.descriptorControlResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 21));
    assertSecurityProofRejected(
        windows ->
            windows.scenario.securityResults.descriptorDaclResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 22));
    assertSecurityProofRejected(
        windows ->
            windows.scenario.securityResults.aclInformationResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 23));
    assertSecurityProofRejected(
        windows ->
            windows.scenario.securityResults.aceResult =
                new WindowsPrivateOutputFileNative.Result<>(0, 24));
    assertSecurityProofRejected(
        windows ->
            windows.scenario.owner.localFreeResult =
                new WindowsPrivateOutputFileNative.Result<>(1L, 25));
  }

  private static void assertOwnerAcquisitionRejected(
      java.util.function.Consumer<SyntheticWin32> mutation) throws IOException {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      mutation.accept(windows);
      assertThrows(IOException.class, () -> WindowsPrivateOutputFileOwner.acquire(windows.calls()));
    }
  }

  private static void assertSingleAceMismatch(java.util.function.Consumer<SyntheticWin32> mutation)
      throws IOException {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      mutation.accept(windows);
      assertFalse(windows.securityProof().exactSingleOwnerFullControlAce());
    }
  }

  private static void assertSecurityProofRejected(
      java.util.function.Consumer<SyntheticWin32> mutation) throws IOException {
    try (SyntheticWin32 windows = new SyntheticWin32()) {
      mutation.accept(windows);
      assertThrows(IOException.class, windows::securityProof);
    }
  }

  /** Complete deterministic Win32 model used to exercise FFM transport behavior without Windows. */
  static final class SyntheticWin32 implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment ownerSid = arena.allocate(8L, Long.BYTES);
    private final MemorySegment sidText = utf16("S-1-5-21-42\0");
    private final MemorySegment securityDescriptor = arena.allocate(8L, Long.BYTES);
    private final MemorySegment dacl = arena.allocate(8L, Long.BYTES);
    private final MemorySegment ace = exactOwnerAce();
    private final List<String> operations = new ArrayList<>();
    private final Scenario scenario = new Scenario();
    private int callTableRequests;

    void createFileError(int value) {
      scenario.entry.createFileError = value;
    }

    void createDirectoryError(int value) {
      scenario.entry.createDirectoryError = value;
    }

    /** Mutable inputs and outcomes partitioned by the Win32 capability they model. */
    private static final class Scenario {
      private final EntryState entry = new EntryState();
      private final HandleState handle = new HandleState();
      private final DescriptorState descriptor = new DescriptorState();
      private final AceState ace = new AceState();
      private final OwnerState owner = new OwnerState();
      private final SecurityResultState securityResults = new SecurityResultState();
    }

    /** Entry-kind and create-operation outcomes. */
    private static final class EntryState {
      private WindowsPrivateOutputFileTransport.EntryKind kind =
          WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE;
      private boolean reparsePoint;
      private int createFileError;
      private int createDirectoryError;
    }

    /** Retained-handle I/O inputs and native return values. */
    private static final class HandleState {
      private int readCount;
      private byte[] readBytes = {};
      private int writeCount = Integer.MIN_VALUE;
      private WindowsPrivateOutputFileNative.Result<Integer> readFileResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> writeFileResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> flushResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> lockResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> unlockResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> closeResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> fileInformationResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> fileSizeResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> filePointerResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> endOfFileResult = intResult(1);
    }

    /** Security-descriptor presence and protection inputs. */
    private static final class DescriptorState {
      private int securityInfoResult;
      private boolean securityDescriptorPresent = true;
      private boolean actualOwnerPresent = true;
      private boolean protectedDacl = true;
      private boolean daclPresent = true;
      private boolean exactDaclPresent = true;
      private boolean securityInfoDaclPresent = true;
    }

    /** The exact DACL ACE shape used by security-proof tests. */
    private static final class AceState {
      private int count = 1;
      private boolean present = true;
      private int sidLength = 8;
      private int type = WindowsPrivateOutputFileNative.ACCESS_ALLOWED_ACE_TYPE;
      private int flags;
      private int size = 16;
      private int mask = WindowsPrivateOutputFileNative.FILE_ALL_ACCESS;
      private boolean equalSid = true;
    }

    /** Current-token-user acquisition and descriptor-construction outcomes. */
    private static final class OwnerState {
      private WindowsPrivateOutputFileNative.Result<Integer> openProcessTokenResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> initialTokenInformationResult =
          new WindowsPrivateOutputFileNative.Result<>(
              0, WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
      private WindowsPrivateOutputFileNative.Result<Integer> finalTokenInformationResult =
          intResult(1);
      private int informationBytes = 8;
      private boolean sidPresent = true;
      private boolean sidTextPresent = true;
      private boolean sidTextTerminated = true;
      private boolean descriptorPresent = true;
      private WindowsPrivateOutputFileNative.Result<Long> localFreeResult = addressResult(0L);
      private WindowsPrivateOutputFileNative.Result<Integer> convertSidResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> descriptorConversionResult =
          intResult(1);
    }

    /** Native result values emitted by descriptor-inspection calls. */
    private static final class SecurityResultState {
      private WindowsPrivateOutputFileNative.Result<Integer> descriptorControlResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> descriptorDaclResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> aclInformationResult = intResult(1);
      private WindowsPrivateOutputFileNative.Result<Integer> aceResult = intResult(1);
    }

    WindowsPrivateOutputFileCalls calls() {
      callTableRequests++;
      return new WindowsPrivateOutputFileCalls(
          new SyntheticHandleCalls(), new SyntheticOwnerCalls(), new SyntheticSecurityCalls());
    }

    int callTableRequests() {
      return callTableRequests;
    }

    List<String> operationNames() {
      return List.copyOf(operations);
    }

    void entryKind(WindowsPrivateOutputFileTransport.EntryKind value) {
      scenario.entry.kind = Objects.requireNonNull(value, "value");
    }

    WindowsPrivateOutputFileHandle handle() {
      return new WindowsPrivateOutputFileHandle(
          calls(), new WindowsPrivateOutputFileNative.Handle(0x77L));
    }

    WindowsPrivateOutputFileHandle handle(
        WindowsPrivateOutputFileHandle.ArenaFactory arenaFactory) {
      return new WindowsPrivateOutputFileHandle(
          calls(), new WindowsPrivateOutputFileNative.Handle(0x77L), arenaFactory);
    }

    void lockFailure(int errorCode) {
      scenario.handle.lockResult = new WindowsPrivateOutputFileNative.Result<>(0, errorCode);
    }

    void unlockFailure(int errorCode) {
      scenario.handle.unlockResult = new WindowsPrivateOutputFileNative.Result<>(0, errorCode);
    }

    void readCount(int value) {
      scenario.handle.readCount = value;
    }

    private WindowsPrivateOutputFileTransport.SecurityProof securityProof() throws IOException {
      return WindowsPrivateOutputFileSecurityProof.read(
          calls(), new WindowsPrivateOutputFileNative.Handle(0x77L), ownerSid);
    }

    @Override
    public void close() {
      arena.close();
    }

    private void record(String operation) {
      operations.add(operation);
    }

    private WindowsPrivateOutputFileNative.Result<Integer> tokenInformation(
        MemorySegment information, int informationLength, MemorySegment returnedLength) {
      returnedLength.set(ValueLayout.JAVA_INT, 0L, scenario.owner.informationBytes);
      if (informationLength == 0) {
        return scenario.owner.initialTokenInformationResult;
      }
      information.set(
          ValueLayout.ADDRESS, 0L, scenario.owner.sidPresent ? ownerSid : MemorySegment.NULL);
      return scenario.owner.finalTokenInformationResult;
    }

    private WindowsPrivateOutputFileNative.Result<Long> createFileResult() {
      if (scenario.entry.createFileError != 0) {
        return new WindowsPrivateOutputFileNative.Result<>(-1L, scenario.entry.createFileError);
      }
      return addressResult(0x77L);
    }

    private WindowsPrivateOutputFileNative.Result<Integer> fileInformation(
        int informationClass, MemorySegment information) {
      if (informationClass == WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_TAG_INFO) {
        int attributes =
            scenario.entry.kind == WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY
                ? WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_DIRECTORY
                : 0;
        if (scenario.entry.reparsePoint) {
          attributes |= WindowsPrivateOutputFileNative.FILE_ATTRIBUTE_REPARSE_POINT;
        }
        information.set(ValueLayout.JAVA_INT, 0L, attributes);
      } else if (informationClass == WindowsPrivateOutputFileNative.FILE_ID_INFO) {
        information.set(ValueLayout.JAVA_LONG, 0L, 73L);
        for (int index = 0; index < 16; index++) {
          information.set(ValueLayout.JAVA_BYTE, Long.BYTES + index, (byte) index);
        }
      } else {
        throw new AssertionError(
            "Unexpected GetFileInformationByHandleEx class: " + informationClass);
      }
      return intResult(1);
    }

    private int securityInfo(
        MemorySegment owner, MemorySegment daclOut, MemorySegment descriptorOut) {
      if (scenario.descriptor.securityInfoResult != 0) {
        return scenario.descriptor.securityInfoResult;
      }
      owner.set(
          ValueLayout.ADDRESS,
          0L,
          scenario.descriptor.actualOwnerPresent ? ownerSid : MemorySegment.NULL);
      daclOut.set(
          ValueLayout.ADDRESS,
          0L,
          scenario.descriptor.securityInfoDaclPresent ? dacl : MemorySegment.NULL);
      descriptorOut.set(
          ValueLayout.ADDRESS,
          0L,
          scenario.descriptor.securityDescriptorPresent ? securityDescriptor : MemorySegment.NULL);
      return scenario.descriptor.securityInfoResult;
    }

    /** Deterministic retained-handle Win32 calls backed by the enclosing synthetic model. */
    private final class SyntheticHandleCalls implements WindowsPrivateOutputFileHandleCalls {
      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> createDirectoryW(
          MemorySegment directory, MemorySegment securityAttributes) {
        record("createDirectoryW");
        return scenario.entry.createDirectoryError == 0
            ? intResult(1)
            : new WindowsPrivateOutputFileNative.Result<>(0, scenario.entry.createDirectoryError);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Long> createFileW(
          MemorySegment fileName,
          int desiredAccess,
          int shareMode,
          MemorySegment securityAttributes,
          int creationDisposition,
          int flagsAndAttributes,
          MemorySegment templateFile) {
        record("createFileW");
        return createFileResult();
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> readFile(
          MemorySegment handle,
          MemorySegment bytes,
          int byteCount,
          MemorySegment transferred,
          MemorySegment overlapped) {
        record("readFile");
        for (int index = 0;
            index < Math.min(scenario.handle.readCount, scenario.handle.readBytes.length);
            index++) {
          bytes.set(ValueLayout.JAVA_BYTE, index, scenario.handle.readBytes[index]);
        }
        transferred.set(ValueLayout.JAVA_INT, 0L, scenario.handle.readCount);
        return scenario.handle.readFileResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> writeFile(
          MemorySegment handle,
          MemorySegment bytes,
          int byteCount,
          MemorySegment transferred,
          MemorySegment overlapped) {
        record("writeFile");
        transferred.set(
            ValueLayout.JAVA_INT,
            0L,
            scenario.handle.writeCount == Integer.MIN_VALUE
                ? byteCount
                : scenario.handle.writeCount);
        return scenario.handle.writeFileResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> flushFileBuffers(MemorySegment handle) {
        record("flushFileBuffers");
        return scenario.handle.flushResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle) {
        record("closeHandle");
        return scenario.handle.closeResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getFileInformationByHandleEx(
          MemorySegment handle, int informationClass, MemorySegment information, int byteCount) {
        record("getFileInformationByHandleEx");
        return scenario.handle.fileInformationResult.value() == 0
            ? scenario.handle.fileInformationResult
            : fileInformation(informationClass, information);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getFileSizeEx(
          MemorySegment handle, MemorySegment size) {
        record("getFileSizeEx");
        size.set(ValueLayout.JAVA_LONG, 0L, 3L);
        return scenario.handle.fileSizeResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> setFilePointerEx(
          MemorySegment handle, long position, MemorySegment newPosition, int moveMethod) {
        record("setFilePointerEx");
        return scenario.handle.filePointerResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> setEndOfFile(MemorySegment handle) {
        record("setEndOfFile");
        return scenario.handle.endOfFileResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> lockFileEx(
          MemorySegment handle,
          int flags,
          int reserved,
          int byteCountLow,
          int byteCountHigh,
          MemorySegment overlapped) {
        record("lockFileEx");
        return scenario.handle.lockResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> unlockFileEx(
          MemorySegment handle,
          int reserved,
          int byteCountLow,
          int byteCountHigh,
          MemorySegment overlapped) {
        record("unlockFileEx");
        return scenario.handle.unlockResult;
      }
    }

    /** Deterministic current-token-user Win32 calls backed by the enclosing synthetic model. */
    private final class SyntheticOwnerCalls implements WindowsPrivateOutputFileOwnerCalls {
      private static final String ACCOUNT_DOMAIN = "RUNNER";
      private static final String ACCOUNT_NAME = "runneradmin";

      @Override
      public WindowsPrivateOutputFileNative.Result<Long> getCurrentProcess() {
        record("getCurrentProcess");
        return addressResult(0x11L);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> openProcessToken(
          MemorySegment process, int desiredAccess, MemorySegment token) {
        record("openProcessToken");
        token.set(ValueLayout.ADDRESS, 0L, MemorySegment.ofAddress(0x22L));
        return scenario.owner.openProcessTokenResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getTokenInformation(
          MemorySegment token,
          int informationClass,
          MemorySegment information,
          int informationLength,
          MemorySegment returnedLength) {
        record("getTokenInformation");
        if (informationClass != WindowsPrivateOutputFileNative.TOKEN_USER) {
          throw new AssertionError("Unexpected GetTokenInformation class: " + informationClass);
        }
        return tokenInformation(information, informationLength, returnedLength);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
        record("localFree");
        return scenario.owner.localFreeResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
          MemorySegment sid, MemorySegment sidTextOut) {
        record("convertSidToStringSidW");
        sidTextOut.set(
            ValueLayout.ADDRESS, 0L, scenario.owner.sidTextPresent ? sidText : MemorySegment.NULL);
        if (!scenario.owner.sidTextTerminated) {
          sidText.fill((byte) 1);
        }
        return scenario.owner.convertSidResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> lookupAccountSidW(
          MemorySegment systemName,
          MemorySegment sid,
          MemorySegment referencedDomainName,
          MemorySegment referencedDomainNameCharacters,
          MemorySegment accountName,
          MemorySegment accountNameCharacters,
          MemorySegment sidNameUse) {
        record("lookupAccountSidW");
        int domainCharacterCount = ACCOUNT_DOMAIN.length() + 1;
        int nameCharacterCount = ACCOUNT_NAME.length() + 1;
        referencedDomainNameCharacters.set(ValueLayout.JAVA_INT, 0L, domainCharacterCount);
        accountNameCharacters.set(ValueLayout.JAVA_INT, 0L, nameCharacterCount);
        if (referencedDomainName.address() == 0L && accountName.address() == 0L) {
          return new WindowsPrivateOutputFileNative.Result<>(
              0, WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
        }
        writeWide(referencedDomainName, ACCOUNT_DOMAIN);
        writeWide(accountName, ACCOUNT_NAME);
        return intResult(1);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer>
          convertStringSecurityDescriptorToSecurityDescriptorW(
              MemorySegment descriptorText,
              int revision,
              MemorySegment descriptorOut,
              MemorySegment descriptorLength) {
        record("convertStringSecurityDescriptorToSecurityDescriptorW");
        descriptorOut.set(
            ValueLayout.ADDRESS,
            0L,
            scenario.owner.descriptorPresent ? securityDescriptor : MemorySegment.NULL);
        descriptorLength.set(ValueLayout.JAVA_INT, 0L, 8);
        return scenario.owner.descriptorConversionResult;
      }
    }

    private void writeWide(MemorySegment destination, String value) {
      byte[] bytes = (value + "\0").getBytes(StandardCharsets.UTF_16LE);
      destination.asSlice(0L, bytes.length).asByteBuffer().put(bytes);
    }

    /** Deterministic security-proof Win32 calls backed by the enclosing synthetic model. */
    private final class SyntheticSecurityCalls implements WindowsPrivateOutputFileSecurityCalls {
      @Override
      public int getSecurityInfo(
          MemorySegment handle,
          int objectType,
          int securityInformation,
          MemorySegment owner,
          MemorySegment group,
          MemorySegment daclOut,
          MemorySegment sacl,
          MemorySegment descriptorOut) {
        record("getSecurityInfo");
        return securityInfo(owner, daclOut, descriptorOut);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorControl(
          MemorySegment descriptor, MemorySegment control, MemorySegment revision) {
        record("getSecurityDescriptorControl");
        control.set(
            ValueLayout.JAVA_SHORT,
            0L,
            scenario.descriptor.protectedDacl
                ? WindowsPrivateOutputFileNative.SE_DACL_PROTECTED
                : (short) 0);
        revision.set(ValueLayout.JAVA_INT, 0L, 1);
        return scenario.securityResults.descriptorControlResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getSecurityDescriptorDacl(
          MemorySegment descriptor,
          MemorySegment daclPresent,
          MemorySegment daclOut,
          MemorySegment defaulted) {
        record("getSecurityDescriptorDacl");
        daclPresent.set(
            ValueLayout.JAVA_INT, 0L, SyntheticWin32.this.scenario.descriptor.daclPresent ? 1 : 0);
        daclOut.set(
            ValueLayout.ADDRESS,
            0L,
            SyntheticWin32.this.scenario.descriptor.exactDaclPresent ? dacl : MemorySegment.NULL);
        defaulted.set(ValueLayout.JAVA_INT, 0L, 0);
        return scenario.securityResults.descriptorDaclResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getAclInformation(
          MemorySegment dacl,
          MemorySegment information,
          int informationLength,
          int informationClass) {
        record("getAclInformation");
        information.set(ValueLayout.JAVA_INT, 0L, scenario.ace.count);
        return scenario.securityResults.aclInformationResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getAce(
          MemorySegment dacl, int index, MemorySegment aceOut) {
        record("getAce");
        writeAce();
        aceOut.set(ValueLayout.ADDRESS, 0L, scenario.ace.present ? ace : MemorySegment.NULL);
        return scenario.securityResults.aceResult;
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> getLengthSid(MemorySegment sid) {
        record("getLengthSid");
        return intResult(scenario.ace.sidLength);
      }

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> equalSid(
          MemorySegment firstSid, MemorySegment secondSid) {
        record("equalSid");
        return intResult(scenario.ace.equalSid ? 1 : 0);
      }
    }

    private MemorySegment utf16(String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
      MemorySegment segment =
          arena.allocate(WindowsPrivateOutputFileNative.MAXIMUM_SID_STRING_BYTES, Character.BYTES);
      segment.asByteBuffer().put(bytes);
      return segment;
    }

    private MemorySegment exactOwnerAce() {
      MemorySegment result = arena.allocate(16L, Long.BYTES).fill((byte) 0);
      result.set(
          ValueLayout.JAVA_BYTE, 0L, (byte) WindowsPrivateOutputFileNative.ACCESS_ALLOWED_ACE_TYPE);
      result.set(ValueLayout.JAVA_SHORT, 2L, (short) 16);
      result.set(ValueLayout.JAVA_INT, 4L, WindowsPrivateOutputFileNative.FILE_ALL_ACCESS);
      return result;
    }

    private void writeAce() {
      ace.set(ValueLayout.JAVA_BYTE, 0L, (byte) scenario.ace.type);
      ace.set(ValueLayout.JAVA_BYTE, 1L, (byte) scenario.ace.flags);
      ace.set(ValueLayout.JAVA_SHORT, 2L, (short) scenario.ace.size);
      ace.set(ValueLayout.JAVA_INT, 4L, scenario.ace.mask);
    }

    private static WindowsPrivateOutputFileNative.Result<Integer> intResult(int value) {
      return new WindowsPrivateOutputFileNative.Result<>(value, 0);
    }

    private static WindowsPrivateOutputFileNative.Result<Long> addressResult(long value) {
      return new WindowsPrivateOutputFileNative.Result<>(value, 0);
    }
  }
}
