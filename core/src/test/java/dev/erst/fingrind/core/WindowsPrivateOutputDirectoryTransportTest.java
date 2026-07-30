package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Proves directory creation uses the same exact ownership contract as protected files. */
class WindowsPrivateOutputDirectoryTransportTest {
  private static final Path PRIVATE_DIRECTORY = Path.of("private-output-directory");
  private static final WindowsPrivateOutputFileTransport.SecurityProof EXACT_DIRECTORY =
      new WindowsPrivateOutputFileTransport.SecurityProof(
          WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY, false, true, true, true, 1, true);

  @Test
  void createsThenProvesTheExactDirectoryThroughOneNativeTokenUserContext() throws IOException {
    FakeOperations operations = new FakeOperations(EXACT_DIRECTORY);

    WindowsPrivateOutputDirectoryTransport.createNew(PRIVATE_DIRECTORY, operations);

    assertEquals(1, operations.createCalls);
    assertEquals(1, operations.openCalls);
    assertTrue(operations.file.closed);
    assertTrue(operations.owner.closed);
    assertTrue(operations.file.provenWithCurrentTokenUser);
  }

  @Test
  void rejectsARegularFileEvenWhenItsOwnerAndDaclFactsAreExact() {
    FakeOperations operations =
        new FakeOperations(
            new WindowsPrivateOutputFileTransport.SecurityProof(
                WindowsPrivateOutputFileTransport.EntryKind.REGULAR_FILE,
                false,
                true,
                true,
                true,
                1,
                true));

    IOException failure =
        assertThrows(
            IOException.class,
            () -> WindowsPrivateOutputDirectoryTransport.createNew(PRIVATE_DIRECTORY, operations));

    assertEquals(
        "A Windows private output directory must remain a non-reparse directory.",
        failure.getMessage());
    assertTrue(operations.file.closed);
    assertTrue(operations.owner.closed);
  }

  @Test
  void closesTheOwnerWhenAtomicCreationFailsBeforeOpeningAProofHandle() {
    FakeOperations operations = new FakeOperations(EXACT_DIRECTORY);
    operations.creationFailure = new IOException("CreateDirectoryW failed");

    IOException failure =
        assertThrows(
            IOException.class,
            () -> WindowsPrivateOutputDirectoryTransport.createNew(PRIVATE_DIRECTORY, operations));

    assertEquals("CreateDirectoryW failed", failure.getMessage());
    assertEquals(1, operations.createCalls);
    assertEquals(0, operations.openCalls);
    assertFalse(operations.file.closed);
    assertTrue(operations.owner.closed);
  }

  /** Test native directory boundary that records creation and proof calls. */
  private static final class FakeOperations
      implements WindowsPrivateOutputDirectoryTransport.NativeDirectoryOperations {
    private final FakeTokenUser owner = new FakeTokenUser();
    private final FakeNativeFile file;
    private int createCalls;
    private int openCalls;
    private @Nullable IOException creationFailure;

    private FakeOperations(WindowsPrivateOutputFileTransport.SecurityProof proof) {
      file = new FakeNativeFile(proof);
    }

    @Override
    public WindowsPrivateOutputFileTransport.CurrentTokenUser acquireCurrentTokenUser() {
      return owner;
    }

    @Override
    public void createDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser)
        throws IOException {
      createCalls++;
      if (creationFailure != null) {
        throw creationFailure;
      }
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile openExistingDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser) {
      openCalls++;
      return file;
    }
  }

  /** Test current-token-user context whose closure is observable. */
  private static final class FakeTokenUser
      implements WindowsPrivateOutputFileTransport.CurrentTokenUser {
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

  /** Test directory handle that exposes one configured native security proof. */
  private static final class FakeNativeFile
      implements WindowsPrivateOutputFileTransport.NativeFile {
    private final WindowsPrivateOutputFileTransport.SecurityProof proof;
    private boolean closed;
    private boolean provenWithCurrentTokenUser;

    private FakeNativeFile(WindowsPrivateOutputFileTransport.SecurityProof proof) {
      this.proof = proof;
    }

    @Override
    public WindowsPrivateOutputFileTransport.SecurityProof securityProof(
        WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser) {
      provenWithCurrentTokenUser =
          tokenUser instanceof FakeTokenUser fakeTokenUser && !fakeTokenUser.closed;
      return proof;
    }

    @Override
    public int read(ByteBuffer destination) {
      return -1;
    }

    @Override
    public int write(ByteBuffer source) {
      int written = source.remaining();
      source.position(source.limit());
      return written;
    }

    @Override
    public long size() {
      return 0L;
    }

    @Override
    public void truncate(long size) {}

    @Override
    public void position(long position) {}

    @Override
    public void force() {}

    @Override
    public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size) {
      return null;
    }

    @Override
    public String physicalObjectIdentity() {
      return "directory";
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
