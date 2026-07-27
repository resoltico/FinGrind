package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct cross-platform coverage for book-key filesystem admission boundaries. */
class SqliteBookKeyFileFilesystemSecurityCoverageTest {
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  @Test
  void ensureSecureParentDirectory_rejectsAParentlessTarget() {
    SqliteCallerPathContractException rejection =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(Path.of("/")));

    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, rejection.pathFailure());
  }

  @Test
  void createNewEmptyFile_claimsAnExactPrivatePosixFile() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentDirectory = fileSystem.path("\\keys");
      parentDirectory.exists = true;
      parentDirectory.regularFile = false;
      parentDirectory.posixPermissions = OWNER_ONLY_DIRECTORY_PERMISSIONS;
      AclFixturePath bookKeyFilePath = fileSystem.path("\\keys\\posix.book-key");

      SqliteSecureRegularFileAccess.createNewEmptyFile(bookKeyFilePath);

      assertTrue(bookKeyFilePath.existsValue());
      assertTrue(bookKeyFilePath.regularFileValue());
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          bookKeyFilePath.posixPermissions);
    }
  }

  @Test
  void ensureSecureParentDirectory_createsAndValidatesNewPrivatePosixParents() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\private\\posix.book-key");
      Path parentDirectory = Objects.requireNonNull(bookKeyFilePath.getParent());

      SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath);

      assertTrue(((AclFixturePath) parentDirectory).existsValue());
      assertEquals(
          OWNER_ONLY_DIRECTORY_PERMISSIONS, ((AclFixturePath) parentDirectory).posixPermissions);
    }
  }

  @Test
  void ensureSecureParentDirectory_rejectsAclOnlyMissingParentsBeforeCreatingThem()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\private\\acl.book-key");
      AclFixturePath parentDirectory =
          assertInstanceOf(AclFixturePath.class, bookKeyFilePath.getParent());

      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath));

      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          failure.pathFailure());
      assertFalse(parentDirectory.existsValue());
    }
  }

  @Test
  void existingSharedParentsAreRejectedWithoutPermissionRepair() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentDirectory = fileSystem.path("\\shared");
      parentDirectory.exists = true;
      parentDirectory.regularFile = false;
      Set<PosixFilePermission> sharedPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ);
      parentDirectory.posixPermissions = sharedPermissions;
      AclFixturePath bookKeyFilePath = fileSystem.path("\\shared\\entity.book-key");

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath));

      assertTrue(Objects.requireNonNull(failure.getMessage()).contains("owner-only permissions"));
      assertEquals(sharedPermissions, parentDirectory.posixPermissions);
    }
  }

  @Test
  void existingOwnerOnlyAclParentsAreValidatedButNewFilesStillFailClosed() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentDirectory = fileSystem.path("\\private");
      parentDirectory.exists = true;
      parentDirectory.regularFile = false;
      Objects.requireNonNull(parentDirectory.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build()));
      AclFixturePath bookKeyFilePath = fileSystem.path("\\private\\acl.book-key");

      SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath);
      SqliteCallerPathContractException creationFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteSecureRegularFileAccess.createNewEmptyFile(bookKeyFilePath));

      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          creationFailure.pathFailure());
      assertFalse(bookKeyFilePath.existsValue());
    }
  }
}
