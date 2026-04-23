package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for platform-specific key-file security branches. */
@NullUnmarked
class SqliteBookKeyFileSecurityTest {

  @Test
  void aclFilesystemBranchesUseOwnerOnlyAclDescriptorsAndGeneration() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath keyPath = fileSystem.path("\\keys\\acme.book-key");

      assertEquals(
          "owner-only-acl", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted();

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertEquals(1, keyPath.aclView.getAcl().size());
      assertEquals(fileSystem.owner, keyPath.aclView.getAcl().getFirst().principal());
      assertTrue(
          keyPath.aclView.getAcl().getFirst().permissions().contains(AclEntryPermission.READ_DATA));
      assertTrue(
          keyPath.aclView.getAcl().getFirst().permissions().contains(AclEntryPermission.DELETE));
    }
  }

  @Test
  void posixFilesystemBranchesUseOwnerOnlyDescriptorsAndGeneration() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath keyPath = fileSystem.path("\\keys\\acme.book-key");

      assertEquals("0600", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted();

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          keyPath.posixPermissions);
    }
  }

  @Test
  void posixFilesystemRejectsOwnerUnreadableAndGroupReadableKeyFiles() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath ownerUnreadable = fileSystem.path("\\keys\\owner-unreadable.book-key");
      ownerUnreadable.exists = true;
      ownerUnreadable.regularFile = true;
      ownerUnreadable.posixPermissions = Set.of(PosixFilePermission.OWNER_WRITE);

      IllegalStateException ownerUnreadableException =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookKeyFileSecurity.requireSecureKeyFile(ownerUnreadable)
                      .requireAccepted());

      assertTrue(ownerUnreadableException.getMessage().contains("owner-readable"));

      AclFixturePath groupReadable = fileSystem.path("\\keys\\group-readable.book-key");
      groupReadable.exists = true;
      groupReadable.regularFile = true;
      groupReadable.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);

      IllegalStateException groupReadableException =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookKeyFileSecurity.requireSecureKeyFile(groupReadable).requireAccepted());

      assertTrue(groupReadableException.getMessage().contains("owner-only permissions"));
    }
  }

  @Test
  void unsupportedFilesystemBranchesRejectWithoutNativeSecurityViews() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath keyPath = fileSystem.path("\\keys\\unsupported.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;

      assertThrows(
          IllegalArgumentException.class,
          () -> SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
    }
  }

  @Test
  void aclFilesystemWithoutAclViewRejectsDuringInspection() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath keyPath = fileSystem.path("\\keys\\missing-view.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView = null;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(exception.getMessage().contains("supports POSIX owner-only permissions"));
    }
  }
}
