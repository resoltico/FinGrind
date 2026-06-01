package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real-host ACL coverage for Windows-owned SQLite filesystem hardening paths. */
class SqliteHostAclCoverageTest {
  @TempDir Path tempDirectory;

  @Test
  void hostAclFilesystemCoversBookKeyAndSnapshotHardeningOnRealFilesystem() throws IOException {
    assumeHostAclFilesystem(tempDirectory);

    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");
    SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
    Files.writeString(bookPath, "book");
    Path walPath = bookPath.resolveSibling("acme.sqlite-wal");
    Files.writeString(walPath, "wal");
    SqliteBookFileSecurity.hardenBookArtifacts(bookPath);
    Path bookDirectory = Objects.requireNonNull(bookPath.getParent(), "bookPath parent");
    assertOwnerOnlyAcl(bookDirectory, Set.of(AclEntryPermission.LIST_DIRECTORY));
    assertOwnerOnlyAcl(bookPath, Set.of(AclEntryPermission.READ_DATA));
    assertOwnerOnlyAcl(walPath, Set.of(AclEntryPermission.READ_DATA));

    Path keyFilePath = tempDirectory.resolve("keys").resolve("acme.book-key");
    SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyFilePath);
    SqliteBookKeyFileSecurity.createSecureEmptyFile(keyFilePath);
    assertEquals(
        keyFilePath, SqliteBookKeyFileSecurity.requireSecureKeyFile(keyFilePath).requireAccepted());
    Path keyDirectory = Objects.requireNonNull(keyFilePath.getParent(), "keyFilePath parent");
    assertOwnerOnlyAcl(keyDirectory, Set.of(AclEntryPermission.LIST_DIRECTORY));
    assertOwnerOnlyAcl(keyFilePath, Set.of(AclEntryPermission.READ_DATA));

    Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve("snapshot"));
    Path snapshotFilePath = snapshotDirectory.resolve("sqlite3.dll");
    Files.writeString(snapshotFilePath, "sqlite3mc");
    SqliteManagedLibraryIdentity.hardenPrivateDirectory(snapshotDirectory);
    SqliteManagedLibraryIdentity.hardenPrivateFile(snapshotFilePath);
    assertOwnerOnlyAcl(snapshotDirectory, Set.of(AclEntryPermission.LIST_DIRECTORY));
    assertOwnerOnlyAcl(snapshotFilePath, Set.of(AclEntryPermission.READ_DATA));
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

  private static void assertOwnerOnlyAcl(Path path, Set<AclEntryPermission> requiredPermissions)
      throws IOException {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    assertNotNull(view);
    List<AclEntry> acl = view.getAcl();
    assertEquals(1, acl.size());
    assertEquals(AclEntryType.ALLOW, acl.getFirst().type());
    assertEquals(view.getOwner(), acl.getFirst().principal());
    assertTrue(acl.getFirst().permissions().containsAll(requiredPermissions));
  }
}
