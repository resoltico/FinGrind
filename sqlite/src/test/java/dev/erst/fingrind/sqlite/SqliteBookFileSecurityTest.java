package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for platform-specific encrypted-book file security branches. */
class SqliteBookFileSecurityTest {
  @TempDir Path tempDirectory;

  @Test
  void aclFilesystemBranchesApplyOwnerOnlyAclToBookAndDirectory() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      SqliteBookFileSecurity.hardenBookArtifacts(bookPath);
      AclFixtureView aclView = Objects.requireNonNull(bookPath.aclView);
      assertEquals(1, aclView.getAcl().size());
      assertEquals(fileSystem.owner, aclView.getAcl().getFirst().principal());
      assertTrue(aclView.getAcl().getFirst().permissions().contains(AclEntryPermission.READ_DATA));
    }
  }

  @Test
  void posixFilesystemBranchesApplyOwnerOnlyModesToBookAndDirectory() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      SqliteBookFileSecurity.hardenBookArtifacts(bookPath);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          bookPath.posixPermissions);
    }
  }

  @Test
  void posixFilesystemRejectsExistingSharedParentDirectoriesInsteadOfMutatingThem() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\shared-parent.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("must already use owner-only permissions"));
    }
  }

  @Test
  void posixFilesystemRejectsParentsMissingOwnerWriteOrExecutePermissions() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\restricted-parent.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions = Set.of(PosixFilePermission.OWNER_READ);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("owner-writable and owner-searchable"));

      parentPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      IllegalStateException missingExecuteException =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(missingExecuteException.getMessage())
              .contains("owner-writable and owner-searchable"));
    }
  }

  @Test
  void aclFilesystemRejectsExistingSharedParentDirectoriesInsteadOfMutatingThem() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\shared-parent.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build(),
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("must grant book-directory access only to the directory owner"));
    }
  }

  @Test
  void aclFilesystemRejectsParentsMissingOwnerTraversalOrWriteAccess() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\restricted-parent.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("must grant the directory owner traversal and write access"));
    }
  }

  @Test
  void aclFilesystemAcceptsOwnerOnlyParentDirectories() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\owner-only.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build(),
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.DENY)
                      .setPrincipal(fileSystem.group)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenBookArtifacts(bookPath));
    }
  }

  @Test
  void supportedSecureFilesystemDelegate_acceptsOwnerOnlyPosixRoots() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\supported.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      assertDoesNotThrow(() -> SqliteBookFileSecurity.requireSupportedSecureFilesystem(bookPath));
    }
  }

  @Test
  void aclFilesystemRejectsNonAllowOwnerEntriesWhenValidatingParents() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\deny-owner.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.DENY)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("must grant the directory owner traversal and write access"));
    }
  }

  @Test
  void unsupportedFilesystemBranchesRejectEncryptedBookStorage() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\unsupported.sqlite");
      IllegalStateException ensureException =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertTrue(
          Objects.requireNonNull(ensureException.getMessage())
              .contains("supports POSIX owner-only permissions"));
      IllegalStateException hardenException =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.hardenBookArtifacts(bookPath));
      assertTrue(
          Objects.requireNonNull(hardenException.getMessage())
              .contains("supports POSIX owner-only permissions"));
    }
  }

  @Test
  void rootLikeOrParentlessBookPathsAreRejectedExplicitly() {
    Path rootPath = tempDirectory.getRoot();
    IllegalArgumentException ensureException =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(rootPath));
    assertTrue(Objects.requireNonNull(ensureException.getMessage()).contains("parent directory"));
    IllegalArgumentException hardenException =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookFileSecurity.hardenBookArtifacts(rootPath));
    assertTrue(Objects.requireNonNull(hardenException.getMessage()).contains("parent directory"));
  }

  @Test
  void hardenBookArtifacts_rejectsNonDirectoryParentsAndMissingAclViews() throws Exception {
    Path missingParentBookPath = tempDirectory.resolve("missing-parent").resolve("book.sqlite");
    assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenBookArtifacts(missingParentBookPath));
    Path parentFile = tempDirectory.resolve("parent-file");
    Files.writeString(parentFile, "not-a-directory");
    Path nestedBookPath = parentFile.resolve("book.sqlite");
    IllegalArgumentException parentFileException =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookFileSecurity.hardenBookArtifacts(nestedBookPath));
    assertTrue(
        Objects.requireNonNull(parentFileException.getMessage()).contains("existing directory"));
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.aclView = null;
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookFileSecurity.hardenBookArtifacts(bookPath));
      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("supports POSIX owner-only permissions"));
    }
  }

  @Test
  void hardenDirectory_ignoresPathsThatAreNotDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath notDirectory = fileSystem.path("\\not-a-directory");
      notDirectory.exists = true;
      notDirectory.regularFile = true;
      assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenDirectory(notDirectory));
    }
  }

  @Test
  void hardenOwnerOnlyFile_ignoresMissingParentDirectories() {
    Path bookPath = tempDirectory.resolve("missing-parent-only-file").resolve("book.sqlite");
    assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenOwnerOnlyFile(bookPath));
  }
}
