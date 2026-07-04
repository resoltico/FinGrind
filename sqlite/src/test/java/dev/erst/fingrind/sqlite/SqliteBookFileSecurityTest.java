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
import org.junit.jupiter.api.function.Executable;
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
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
          "must already use owner-only permissions",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
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
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "owner-writable and owner-searchable",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

      parentPath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "owner-writable and owner-searchable",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
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
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
          "must grant book-directory access only to the directory owner",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
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
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "must grant the directory owner traversal and write access",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
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
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "must grant the directory owner traversal and write access",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
    }
  }

  @Test
  void unsupportedFilesystemBranchesRejectEncryptedBookStorage() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\unsupported.sqlite");
      assertPathFailure(
          parentPath,
          SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
          "supports POSIX owner-only permissions",
          () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));
      assertPathFailure(
          bookPath,
          SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
          "supports POSIX owner-only permissions",
          () -> SqliteBookFileSecurity.hardenBookArtifacts(bookPath));
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
    assertPathFailure(
        nestedBookPath,
        SqliteCallerPathFailure.PARENT_PATH_COLLISION,
        "requires a real parent directory",
        () -> SqliteBookFileSecurity.hardenBookArtifacts(nestedBookPath));
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

  private static void assertPathFailure(
      Path expectedPath,
      SqliteCallerPathFailure expectedFailure,
      String expectedMessageFragment,
      Executable executable) {
    SqliteCallerPathContractException exception =
        assertThrows(SqliteCallerPathContractException.class, executable);
    assertEquals(expectedPath, exception.requestedPath());
    assertEquals(expectedFailure, exception.pathFailure());
    assertTrue(NullTestSupport.messageOf(exception).contains(expectedMessageFragment));
  }
}
