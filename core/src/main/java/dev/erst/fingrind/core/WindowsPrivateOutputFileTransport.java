package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Windows owner-only file protocol expressed independently from the Win32 FFM binding.
 *
 * <p>The transport admits only a retained non-reparse regular handle whose owner, protected DACL,
 * and single full-control owner ACE have all been proven by the native binding. Keeping the proof
 * model here makes every security branch deterministic under a test binding while the FFM class
 * remains a narrow ABI adapter.
 */
final class WindowsPrivateOutputFileTransport {
  private WindowsPrivateOutputFileTransport() {}

  static PrivateOutputFile.OpenedFile createNew(Path file, NativeFileOperations operations)
      throws IOException {
    Path checkedFile = Objects.requireNonNull(file, "file");
    NativeFileOperations checkedOperations = Objects.requireNonNull(operations, "operations");
    try (CurrentTokenUser tokenUser = checkedOperations.acquireCurrentTokenUser()) {
      NativeFile opened = checkedOperations.createNew(checkedFile, tokenUser);
      return retainAfterProof(opened, tokenUser, true);
    }
  }

  static PrivateOutputFile.OpenedFile openExisting(
      Path file, PrivateOutputFile.Access access, NativeFileOperations operations)
      throws IOException {
    Path checkedFile = Objects.requireNonNull(file, "file");
    PrivateOutputFile.Access checkedAccess = Objects.requireNonNull(access, "access");
    NativeFileOperations checkedOperations = Objects.requireNonNull(operations, "operations");
    try (CurrentTokenUser tokenUser = checkedOperations.acquireCurrentTokenUser()) {
      NativeFile opened = checkedOperations.openExisting(checkedFile, checkedAccess, tokenUser);
      return retainAfterProof(opened, tokenUser, false);
    }
  }

  static void requireExactOwnerOnly(SecurityProof proof) throws IOException {
    requireExactOwnerOnly(proof, EntryKind.REGULAR_FILE);
  }

  static void requireExactOwnerOnlyDirectory(SecurityProof proof) throws IOException {
    requireExactOwnerOnly(proof, EntryKind.DIRECTORY);
  }

  private static void requireExactOwnerOnly(SecurityProof proof, EntryKind requiredKind)
      throws IOException {
    SecurityProof checkedProof = Objects.requireNonNull(proof, "proof");
    EntryKind checkedKind = Objects.requireNonNull(requiredKind, "requiredKind");
    if (checkedProof.entryKind() != checkedKind || checkedProof.reparsePoint()) {
      throw new IOException(
          "A Windows private output "
              + checkedKind.description()
              + " must remain a non-reparse "
              + checkedKind.description()
              + ".");
    }
    if (!checkedProof.ownerMatchesCurrentTokenUser()) {
      throw new IOException(
          "A Windows private output "
              + checkedKind.description()
              + " must be owned by the current token user.");
    }
    if (!checkedProof.protectedDacl()) {
      throw new IOException(
          "A Windows private output "
              + checkedKind.description()
              + " must retain a protected DACL.");
    }
    if (!checkedProof.explicitNonNullDacl()) {
      throw new IOException(
          "A Windows private output "
              + checkedKind.description()
              + " must retain an explicit non-null DACL.");
    }
    if (checkedProof.aceCount() != 1 || !checkedProof.exactSingleOwnerFullControlAce()) {
      throw new IOException(
          "A Windows private output "
              + checkedKind.description()
              + " must retain exactly one full-control owner ACE.");
    }
  }

  private static PrivateOutputFile.OpenedFile retainAfterProof(
      NativeFile opened, CurrentTokenUser tokenUser, boolean created) throws IOException {
    NativeFile checkedOpened = Objects.requireNonNull(opened, "opened");
    CurrentTokenUser checkedTokenUser = Objects.requireNonNull(tokenUser, "tokenUser");
    try {
      requireExactOwnerOnly(checkedOpened.securityProof(checkedTokenUser));
      return new WindowsPrivateOutputFileRetainedFile(checkedOpened, created);
    } catch (IOException | RuntimeException | Error failure) {
      closePreservingFailure(checkedOpened, failure);
      throw failure;
    }
  }

  private static void closePreservingFailure(NativeFile file, Throwable failure) {
    try {
      file.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  /** Native token-user identity retained only while a file's current DACL is validated. */
  @FunctionalInterface
  interface CurrentTokenUser extends AutoCloseable {
    /** Returns the canonical SID text used in the exact protected creation descriptor. */
    String ownerSidText();

    @Override
    default void close() throws IOException {
      // Stateless test owners retain no native resource.
    }
  }

  /** Testable native binding boundary; no raw handle crosses the core public API. */
  interface NativeFileOperations {
    /** Acquires current-token-user evidence for one native file operation. */
    CurrentTokenUser acquireCurrentTokenUser() throws IOException;

    /** Atomically creates the file through the current token user's protected descriptor. */
    NativeFile createNew(Path file, CurrentTokenUser tokenUser) throws IOException;

    /**
     * Opens an existing file for the requested access while retaining current-token-user evidence.
     */
    NativeFile openExisting(Path file, PrivateOutputFile.Access access, CurrentTokenUser tokenUser)
        throws IOException;
  }

  /** Native handle operations retained by the owner-only core capability. */
  interface NativeFile extends AutoCloseable {
    /**
     * Proves the handle's exact descriptor while the current-token-user evidence remains alive.
     *
     * <p>The owner context is deliberately an argument rather than retained state: native SID
     * storage belongs to the short proof scope and must never escape with the file capability.
     */
    SecurityProof securityProof(CurrentTokenUser tokenUser) throws IOException;

    /** Reads into the destination through this retained native file handle. */
    int read(ByteBuffer destination) throws IOException;

    /** Writes from the source through this retained native file handle. */
    int write(ByteBuffer source) throws IOException;

    /** Reports the retained native file's byte length. */
    long size() throws IOException;

    /** Truncates the retained native file to the specified byte length. */
    void truncate(long size) throws IOException;

    /** Moves the retained native file's logical cursor. */
    void position(long position) throws IOException;

    /** Flushes the retained native file's content and metadata. */
    void force() throws IOException;

    /** Attempts a retained exclusive byte-range lock. */
    PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
        throws IOException;

    /** Returns the physical identity represented by the retained native file handle. */
    String physicalObjectIdentity() throws IOException;

    /** Closes the retained native file handle. */
    @Override
    void close() throws IOException;
  }

  /** Exact facts read from one native handle's security descriptor and file attributes. */
  record SecurityProof(
      EntryKind entryKind,
      boolean reparsePoint,
      boolean ownerMatchesCurrentTokenUser,
      boolean protectedDacl,
      boolean explicitNonNullDacl,
      int aceCount,
      boolean exactSingleOwnerFullControlAce) {
    SecurityProof {
      Objects.requireNonNull(entryKind, "entryKind");
      if (aceCount < 0) {
        throw new IllegalArgumentException("aceCount must be non-negative.");
      }
    }
  }

  /** Exact native handle type needed by the protected private-output capability. */
  enum EntryKind {
    REGULAR_FILE("file"),
    DIRECTORY("directory");

    private final String description;

    EntryKind(String description) {
      this.description = description;
    }

    private String description() {
      return description;
    }
  }
}
