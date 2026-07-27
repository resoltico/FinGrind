package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** Tests for platform-specific encrypted-book file security branches. */
class SqliteBookFileSecurityTest {
  @TempDir Path tempDirectory;

  @Test
  void aclFilesystemRefusesNewBookParentCreationWithoutAclRepair() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      AclFixturePath parentPath = fileSystem.path("\\books");
      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          failure.pathFailure());
      assertFalse(parentPath.exists);
    }
  }

  @Test
  void createNewOwnerOnlyBookFile_claimsAnExactPrivatePosixFile() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);

      SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath);

      assertTrue(bookPath.exists);
      assertTrue(bookPath.regularFile);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          bookPath.posixPermissions);
      assertThrows(
          FileAlreadyExistsException.class,
          () -> SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath));
    }
  }

  @Test
  void createNewOwnerOnlyBookFileRejectsNonemptyAndUnsupportedExactCreationChannels()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);

      AclFixturePath nonemptyBookPath = fileSystem.path("\\books\\nonempty.sqlite");
      nonemptyBookPath.reportSizeAs(1L);
      IOException nonemptyFailure =
          assertThrows(
              IOException.class,
              () -> SqliteBookFileSecurity.createNewOwnerOnlyBookFile(nonemptyBookPath));
      assertTrue(NullTestSupport.messageOf(nonemptyFailure).contains("was not empty"));

      AclFixturePath unsupportedBookPath = fileSystem.path("\\books\\unsupported.sqlite");
      unsupportedBookPath.failNewFileChannelWithUnsupportedOperation(
          new UnsupportedOperationException("injected atomic creation refusal"));
      assertPathFailure(
          unsupportedBookPath,
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          "cannot atomically create",
          () -> SqliteBookFileSecurity.createNewOwnerOnlyBookFile(unsupportedBookPath));
    }
  }

  @Test
  void requireSecureExistingBookFile_acceptsPrivatePosixPermissionsWithoutMutatingThem()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\existing.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      Set<PosixFilePermission> originalPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.posixPermissions = originalPermissions;

      assertDoesNotThrow(
          () -> SqliteBookFileSecurity.requireSecureExistingBookFile(bookPath, true));

      assertEquals(originalPermissions, bookPath.posixPermissions);
    }
  }

  @Test
  void requireSecureExistingBookFile_rejectsSharedPosixPermissionsWithoutRepairingThem()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      AclFixturePath bookPath = fileSystem.path("\\books\\shared.sqlite");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      Set<PosixFilePermission> sharedPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.posixPermissions = sharedPermissions;

      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteBookFileSecurity.requireSecureExistingBookFile(bookPath, true));

      assertEquals(SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED, failure.pathFailure());
      assertEquals(sharedPermissions, bookPath.posixPermissions);
    }
  }

  @Test
  void requireExistingSecureParentDirectory_refusesAMissingParentWithoutCreatingIt()
      throws Exception {
    Path bookPath = tempDirectory.resolve("missing-maintenance-parent").resolve("book.sqlite");
    Path parent = Objects.requireNonNull(bookPath.getParent(), "bookPath parent");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.requireExistingSecureParentDirectory(bookPath));

    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, failure.pathFailure());
    assertFalse(Files.exists(parent));
  }

  @Test
  void ensureSecureParentDirectory_rejectsMutableExistingAncestryBeforeCreatingAnyDescendant()
      throws Exception {
    Assumptions.assumeTrue(
        tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "host filesystem lacks POSIX permissions");
    Path canonicalTemporaryDirectory = tempDirectory.toRealPath();
    Path mutableAncestor =
        Files.createDirectory(canonicalTemporaryDirectory.resolve("mutable-ancestor"));
    Files.setPosixFilePermissions(
        mutableAncestor,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE));
    Path bookPath = mutableAncestor.resolve("new-private-parent").resolve("book.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

    assertEquals(SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED, failure.pathFailure());
    assertFalse(Files.exists(Objects.requireNonNull(bookPath.getParent())));
  }

  @Test
  void ensureSecureParentDirectory_rejectsOneDirectParentSymbolicLink() throws Exception {
    Path realParent = Files.createDirectory(tempDirectory.resolve("real-parent"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(realParent);
    Path symbolicParent = tempDirectory.resolve("symbolic-parent");
    try {
      Files.createSymbolicLink(symbolicParent, realParent);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assumptions.assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }
    Path bookPath = symbolicParent.resolve("book.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
  }

  @Test
  void ensureSecureParentDirectory_rejectsAnIntermediateSymbolicLinkBeforeCreatingItsTarget()
      throws Exception {
    Path canonicalTemporaryDirectory = tempDirectory.toRealPath();
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(canonicalTemporaryDirectory);
    Path redirectTarget =
        Files.createDirectory(canonicalTemporaryDirectory.resolve("redirect-target"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(redirectTarget);
    Path redirect = canonicalTemporaryDirectory.resolve("redirect");
    try {
      Files.createSymbolicLink(redirect, redirectTarget);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assumptions.assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }
    Path redirectedParent = redirect.resolve("missing-private-parent");
    Path bookPath = redirectedParent.resolve("book.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
    assertFalse(Files.exists(redirectTarget.resolve("missing-private-parent")));
  }

  @Test
  void ensureSecureParentDirectory_rejectsAnIntermediateSymbolicLinkForAnExistingParent()
      throws Exception {
    Path canonicalTemporaryDirectory = tempDirectory.toRealPath();
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(canonicalTemporaryDirectory);
    Path redirectTarget =
        Files.createDirectory(canonicalTemporaryDirectory.resolve("redirect-target"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(redirectTarget);
    Path existingPrivateParent = Files.createDirectory(redirectTarget.resolve("existing-private"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(existingPrivateParent);
    Path redirect = canonicalTemporaryDirectory.resolve("redirect");
    try {
      Files.createSymbolicLink(redirect, redirectTarget);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assumptions.assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }
    Path bookPath = redirect.resolve("existing-private").resolve("book.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
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
  void requireSecureExistingBookFile_acceptsOwnerOnlyAclWithoutRepairingIt() throws Exception {
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
      bookPath.exists = true;
      bookPath.regularFile = true;
      List<AclEntry> originalAcl =
          List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.owner)
                  .setPermissions(
                      AclEntryPermission.READ_DATA,
                      AclEntryPermission.WRITE_DATA,
                      AclEntryPermission.APPEND_DATA,
                      AclEntryPermission.READ_NAMED_ATTRS,
                      AclEntryPermission.WRITE_NAMED_ATTRS,
                      AclEntryPermission.READ_ATTRIBUTES,
                      AclEntryPermission.WRITE_ATTRIBUTES,
                      AclEntryPermission.DELETE,
                      AclEntryPermission.READ_ACL,
                      AclEntryPermission.SYNCHRONIZE)
                  .build());
      Objects.requireNonNull(bookPath.aclView).setAcl(originalAcl);

      assertDoesNotThrow(
          () -> SqliteBookFileSecurity.requireSecureExistingBookFile(bookPath, true));

      assertEquals(originalAcl, Objects.requireNonNull(bookPath.aclView).getAcl());
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
          () -> SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath));
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
    IllegalArgumentException createException =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookFileSecurity.createNewOwnerOnlyBookFile(rootPath));
    assertTrue(Objects.requireNonNull(createException.getMessage()).contains("parent directory"));
  }

  @Test
  void requireSecureExistingBookFile_rejectsNonDirectoryParents() throws Exception {
    Path parentFile = tempDirectory.resolve("parent-file");
    Files.writeString(parentFile, "not-a-directory");
    Path nestedBookPath = parentFile.resolve("book.sqlite");
    assertPathFailure(
        nestedBookPath,
        SqliteCallerPathFailure.PARENT_PATH_COLLISION,
        "requires a real parent directory",
        () -> SqliteBookFileSecurity.requireSecureExistingBookFile(nestedBookPath, true));
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
