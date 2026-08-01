package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real-host ACL coverage for Windows-owned protected-book and key-file admission paths. */
class SqliteHostAclCoverageTest {
  @TempDir Path tempDirectory;

  @Test
  void hostAclFilesystemRefusesNewBookAndSecretStageCreationWithoutAclRepair() throws IOException {
    assumeHostAclFilesystem(tempDirectory);

    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");
    SqliteCallerPathContractException bookCreationFailure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
    assertEquals(
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        bookCreationFailure.pathFailure());
    assertTrue(Files.notExists(Objects.requireNonNull(bookPath.getParent(), "bookPath parent")));

    Path keyFilePath = tempDirectory.resolve("keys").resolve("acme.book-key");
    SqliteCallerPathContractException keyParentCreationFailure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyFilePath));
    assertEquals(
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        keyParentCreationFailure.pathFailure());
    Path keyDirectory = Objects.requireNonNull(keyFilePath.getParent(), "keyFilePath parent");
    assertTrue(Files.notExists(keyDirectory));
    assertTrue(Files.notExists(keyFilePath));
  }

  private static void assumeHostAclFilesystem(Path existingPath) {
    assumeTrue(
        existingPath.getFileSystem().supportedFileAttributeViews().contains("acl"),
        "host filesystem lacks ACL view support");
    assumeTrue(
        Files.getFileAttributeView(
                existingPath, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            != null,
        "host path lacks ACL view");
  }
}
