package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Verifies Windows policy independently from the host's native ABI. */
class WindowsPrivateOutputFileTransportTest {
  private static final Path PRIVATE_FILE = Path.of("private-output.fg");
  private static final WindowsPrivateOutputFileTransport.SecurityProof EXACT_PROOF =
      new WindowsPrivateOutputFileTransport.SecurityProof(
          WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
          false,
          true,
          true,
          true,
          1,
          true);

  @Test
  void createNewRetainsOnlyTheExactProvenNativeHandle() throws IOException {
    FakeOperations operations = new FakeOperations(EXACT_PROOF);

    try (PrivateOutputFile.OpenedFile opened =
        WindowsPrivateOutputFileTransport.createNew(PRIVATE_FILE, operations)) {
      assertTrue(opened.created());
      assertSame(PRIVATE_FILE, operations.createdPath);
      assertEquals(1, operations.createCalls);
      assertEquals(0, operations.openCalls);
      assertFalse(operations.file.closed);
      assertSame(operations.owner, operations.file.ownerAtProof);
      assertFalse(operations.file.ownerWasClosedDuringProof);
    }

    assertTrue(operations.file.closed);
    assertTrue(operations.owner.closed);
  }

  @Test
  void openExistingPreservesTheRequestedAccessAndDoesNotClaimCreation() throws IOException {
    FakeOperations operations = new FakeOperations(EXACT_PROOF);

    try (PrivateOutputFile.OpenedFile opened =
        WindowsPrivateOutputFileTransport.openExisting(
            PRIVATE_FILE, PrivateOutputFile.Access.READ_ONLY, operations)) {
      assertFalse(opened.created());
      assertSame(PRIVATE_FILE, operations.openedPath);
      assertEquals(PrivateOutputFile.Access.READ_ONLY, operations.openedAccess);
      assertEquals(0, operations.createCalls);
      assertEquals(1, operations.openCalls);
      assertSame(operations.owner, operations.file.ownerAtProof);
      assertFalse(operations.file.ownerWasClosedDuringProof);
    }

    assertTrue(operations.file.closed);
    assertTrue(operations.owner.closed);
  }

  @Test
  void rejectsEveryIncompleteExactOwnerOnlyProofAndClosesTheUntrustedHandle() throws IOException {
    List<WindowsPrivateOutputFileTransport.SecurityProof> proofs =
        List.of(
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY,
                false,
                true,
                true,
                true,
                1,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                true,
                true,
                true,
                true,
                1,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                false,
                true,
                true,
                1,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                false,
                true,
                1,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                true,
                false,
                1,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                true,
                true,
                0,
                true),
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                true,
                true,
                1,
                false));

    for (WindowsPrivateOutputFileTransport.SecurityProof proof : proofs) {
      assertIncompleteProofRejected(proof);
    }
  }

  @Test
  void preservesTheProofFailureWhenClosingTheUntrustedHandleAlsoFails() {
    IOException closeFailure = new IOException("native close failed");
    FakeOperations operations =
        new FakeOperations(
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                false,
                true,
                true,
                1,
                true));
    operations.file.closeFailure = closeFailure;

    IOException failure =
        assertThrows(
            IOException.class,
            () -> WindowsPrivateOutputFileTransport.createNew(PRIVATE_FILE, operations));

    assertEquals(
        "A Windows private output file must be owned by the current token owner.",
        failure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertSame(closeFailure, failure.getSuppressed()[0]);
  }

  @Test
  void retainedFileDelegatesTheSingleCapabilityWithoutPathReopening() throws IOException {
    FakeOperations operations = new FakeOperations(EXACT_PROOF);
    operations.file.readResult = 3;
    operations.file.size = 41L;

    try (PrivateOutputFile.OpenedFile opened =
        WindowsPrivateOutputFileTransport.createNew(PRIVATE_FILE, operations)) {
      assertTrue(operations.owner.closed);
      ByteBuffer destination = ByteBuffer.allocate(3);
      assertEquals(3, opened.read(destination));
      assertEquals(3, opened.write(ByteBuffer.wrap(new byte[] {1, 2, 3})));
      assertEquals(41L, opened.size());
      opened.truncate(7L);
      opened.position(11L);
      opened.force();
      assertEquals("physical-file", opened.physicalObjectIdentity());
      try (PrivateOutputFile.HeldLock ignored = opened.tryExclusiveLock(0L, 1L)) {
        assertFalse(operations.file.lockClosed);
      }
      assertTrue(operations.file.lockClosed);
    }

    assertEquals(7L, operations.file.truncatedTo);
    assertEquals(11L, operations.file.positionedTo);
    assertTrue(operations.file.forced);
  }

  @Test
  void retainedFileClosesExactlyOnceAndRefusesEveryLaterCapabilityUse() throws IOException {
    FakeOperations operations = new FakeOperations(EXACT_PROOF);

    try (PrivateOutputFile.OpenedFile opened =
        WindowsPrivateOutputFileTransport.createNew(PRIVATE_FILE, operations)) {
      opened.close();
      opened.close();

      assertFalse(opened.isOpen());
      assertEquals(1, operations.file.closeCalls);
      assertThrows(IOException.class, opened::force);
    }
  }

  @Test
  void ownerOnlyDescriptorIsTheProtectedOneAceContract() {
    assertEquals(
        "O:S-1-5-21-42D:P(A;;FA;;;S-1-5-21-42)",
        WindowsPrivateOutputFileFfmTransport.protectedOwnerOnlyDescriptor("S-1-5-21-42"));
  }

  @Test
  void statelessCurrentOwnerCanCloseWithoutRetainingANativeResource() throws IOException {
    try (WindowsPrivateOutputFileTransport.CurrentOwner owner = () -> "S-1-5-21-42") {
      assertEquals("S-1-5-21-42", owner.ownerSidText());
    }
  }

  @Test
  void securityProofRejectsNegativeAceCount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                true,
                true,
                -1,
                true));
  }

  private void assertIncompleteProofRejected(WindowsPrivateOutputFileTransport.SecurityProof proof)
      throws IOException {
    FakeOperations operations = new FakeOperations(proof);

    assertThrows(
        IOException.class,
        () -> WindowsPrivateOutputFileTransport.createNew(PRIVATE_FILE, operations));

    assertTrue(operations.file.closed);
    assertTrue(operations.owner.closed);
  }

  /** Test native-file boundary that records creation and admission attempts. */
  private static final class FakeOperations
      implements WindowsPrivateOutputFileTransport.NativeFileOperations {
    private final FakeOwner owner = new FakeOwner();
    private final FakeNativeFile file;
    private @Nullable Path createdPath;
    private @Nullable Path openedPath;
    private PrivateOutputFile.@Nullable Access openedAccess;
    private int createCalls;
    private int openCalls;

    private FakeOperations(WindowsPrivateOutputFileTransport.SecurityProof proof) {
      file = new FakeNativeFile(proof);
    }

    @Override
    public WindowsPrivateOutputFileTransport.CurrentOwner acquireCurrentOwner() {
      return owner;
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile createNew(
        Path file, WindowsPrivateOutputFileTransport.CurrentOwner owner) {
      createdPath = file;
      createCalls++;
      return this.file;
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile openExisting(
        Path file,
        PrivateOutputFile.Access access,
        WindowsPrivateOutputFileTransport.CurrentOwner owner) {
      openedPath = file;
      openedAccess = access;
      openCalls++;
      return this.file;
    }
  }

  /** Test current-owner evidence whose closure is observable. */
  private static final class FakeOwner implements WindowsPrivateOutputFileTransport.CurrentOwner {
    private boolean closed;

    @Override
    public String ownerSidText() {
      return "S-1-5-21-42";
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** Test native file handle with configurable proof, data behavior, and closure failure. */
  private static final class FakeNativeFile
      implements WindowsPrivateOutputFileTransport.NativeFile {
    private final WindowsPrivateOutputFileTransport.SecurityProof proof;
    private int readResult = -1;
    private long size;
    private long truncatedTo = -1L;
    private long positionedTo = -1L;
    private boolean forced;
    private boolean lockClosed;
    private boolean closed;
    private int closeCalls;
    private @Nullable IOException closeFailure;
    private WindowsPrivateOutputFileTransport.@Nullable CurrentOwner ownerAtProof;
    private boolean ownerWasClosedDuringProof;

    private FakeNativeFile(WindowsPrivateOutputFileTransport.SecurityProof proof) {
      this.proof = proof;
    }

    @Override
    public WindowsPrivateOutputFileTransport.SecurityProof securityProof(
        WindowsPrivateOutputFileTransport.CurrentOwner owner) {
      ownerAtProof = owner;
      ownerWasClosedDuringProof = owner instanceof FakeOwner fakeOwner && fakeOwner.closed;
      return proof;
    }

    @Override
    public int read(ByteBuffer destination) {
      for (int index = 0; index < readResult; index++) {
        destination.put((byte) index);
      }
      return readResult;
    }

    @Override
    public int write(ByteBuffer source) {
      int written = source.remaining();
      source.position(source.limit());
      return written;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public void truncate(long size) {
      truncatedTo = size;
    }

    @Override
    public void position(long position) {
      positionedTo = position;
    }

    @Override
    public void force() {
      forced = true;
    }

    @Override
    public PrivateOutputFile.HeldLock tryExclusiveLock(long position, long size) {
      return () -> lockClosed = true;
    }

    @Override
    public String physicalObjectIdentity() {
      return "physical-file";
    }

    @Override
    public void close() throws IOException {
      closeCalls++;
      closed = true;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }
}
