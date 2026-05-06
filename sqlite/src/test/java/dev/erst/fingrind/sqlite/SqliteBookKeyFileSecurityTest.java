package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
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
      AclFixturePath parentPath = fileSystem.path("\\keys");

      assertEquals(
          "owner-only-acl", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted();

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertTrue(parentPath.exists);
      assertFalse(parentPath.regularFile);
      assertEquals(1, parentPath.aclView.getAcl().size());
      assertEquals(fileSystem.owner, parentPath.aclView.getAcl().getFirst().principal());
      assertTrue(
          parentPath
              .aclView
              .getAcl()
              .getFirst()
              .permissions()
              .contains(AclEntryPermission.LIST_DIRECTORY));
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
      AclFixturePath parentPath = fileSystem.path("\\keys");

      assertEquals("0600", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted();

      assertTrue(keyPath.exists);
      assertTrue(keyPath.regularFile);
      assertTrue(parentPath.exists);
      assertFalse(parentPath.regularFile);
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          parentPath.posixPermissions);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          keyPath.posixPermissions);
    }
  }

  @Test
  void posixFilesystemRejectsOwnerUnreadableAndGroupReadableKeyFiles() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
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
  void posixFilesystemRejectsKeyFilesInsideNonOwnerOnlyParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\shared-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE);
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(
          exception.getMessage().contains("parent directory must use owner-only permissions"));
    }
  }

  @Test
  void posixFilesystemRejectsKeyFilesInsideNonSearchableParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\non-searchable-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(exception.getMessage().contains("parent directory must be owner-searchable"));
    }
  }

  @Test
  void aclFilesystemRejectsKeyFilesInsideSharedParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\shared-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                  .build(),
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.group)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                  .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.READ_DATA)
                  .build()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(
          exception
              .getMessage()
              .contains("parent directory ACL must grant secret-directory access only"));
      assertTrue(exception.getMessage().contains(fileSystem.group.getName()));
    }
  }

  @Test
  void aclFilesystemRejectsKeyFilesInsideNonTraversableParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\non-traversable-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                  .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.READ_DATA)
                  .build()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(
          exception
              .getMessage()
              .contains("parent directory ACL must grant the directory owner traversal access"));
    }
  }

  @Test
  void aclFilesystemIgnoresDeniedEntriesWhileAcceptingOwnerOnlyParentSecurity() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\deny-entry-owner-only.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.DENY)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.DELETE_CHILD)
                  .build(),
              AclEntry.newBuilder()
                  .setType(AclEntryType.DENY)
                  .setPrincipal(fileSystem.group)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                  .build(),
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                  .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.READ_DATA)
                  .build()));

      assertEquals(
          keyPath, SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
    }
  }

  @Test
  void keyFileInspectionRejectsPathsWithoutResolvableParentDirectory() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath keyPath = fileSystem.path("orphan.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(exception.getMessage().contains("resolve beneath an existing parent directory"));
    }
  }

  @Test
  void keyFileInspectionRejectsPathsWhoseParentResolvesToARegularFile() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\not-a-directory");
      AclFixturePath keyPath = fileSystem.path("\\not-a-directory\\orphaned-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = true;
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());

      assertTrue(exception.getMessage().contains("resolve beneath an existing parent directory"));
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
  void hardenDirectory_ignoresNonDirectoryPaths() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath filePath = fileSystem.path("\\keys\\not-a-directory");
      filePath.exists = true;
      filePath.regularFile = true;

      SqliteBookKeyFileSecurity.hardenDirectory(filePath);

      assertTrue(filePath.exists);
      assertTrue(filePath.regularFile);
    }
  }

  @Test
  void aclFilesystemWithoutAclViewRejectsDuringInspection() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.aclView.setAcl(
          java.util.List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                  .build()));
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
