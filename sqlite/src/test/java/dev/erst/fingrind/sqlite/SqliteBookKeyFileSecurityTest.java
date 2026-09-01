package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for platform-specific key-file security branches. */
class SqliteBookKeyFileSecurityTest {
  @TempDir Path temporaryDirectory;

  @Test
  void aclFilesystemRefusesMissingParentCreationWithoutAclRepair() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath keyPath = fileSystem.path("\\keys\\acme.book-key");
      AclFixturePath parentPath = fileSystem.path("\\keys");
      assertEquals(
          "owner-only-acl", SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(keyPath);
      SqliteCallerPathContractException parentCreationFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath));
      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          parentCreationFailure.pathFailure());
      assertFalse(keyPath.existsValue());
      assertFalse(parentPath.existsValue());
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
      SqliteOwnedRegularFileAccess.createNewEmptyFile(keyPath);
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted();
      assertTrue(keyPath.existsValue());
      assertTrue(keyPath.regularFileValue());
      assertTrue(parentPath.existsValue());
      assertFalse(parentPath.regularFileValue());
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
  void ensureSecureParentDirectory_rejectsAnIntermediateSymbolicLinkBeforeCreatingItsTarget()
      throws Exception {
    Assumptions.assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "host filesystem lacks POSIX permissions");
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
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
    Path keyPath = redirectedParent.resolve("book.key");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
    assertFalse(Files.exists(redirectTarget.resolve("missing-private-parent")));
  }

  @Test
  void ensureSecureParentDirectory_rejectsAnIntermediateSymbolicLinkForAnExistingParent()
      throws Exception {
    Assumptions.assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "host filesystem lacks POSIX permissions");
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
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
    Path keyPath = redirect.resolve("existing-private").resolve("book.key");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
  }

  @Test
  void ensureSecureParentDirectory_rejectsExistingSharedPosixDirectories() {
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
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath));
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("parent directory must use owner-only permissions"));
    }
  }

  @Test
  void ensureSecureParentDirectory_rejectsExistingSharedAclDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\shared-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.ensureSecureParentDirectory(keyPath));
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("parent directory ACL must grant secret-directory access only"));
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
      assertTrue(NullTestSupport.messageOf(ownerUnreadableException).contains("owner-readable"));
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
      assertTrue(
          NullTestSupport.messageOf(groupReadableException).contains("owner-only permissions"));
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
      ContractFailureException exception =
          assertThrows(
              ContractFailureException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("parent directory must use owner-only permissions"));
      assertTrue(
          java.util.Objects.requireNonNull(exception.failure().hint())
              .contains("Create a private owner-only parent directory yourself"));
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
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("parent directory must be owner-searchable"));
    }
  }

  @Test
  void aclFilesystemRejectsKeyFilesInsideSharedParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\shared-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      Objects.requireNonNull(keyPath.aclView)
          .setAcl(
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
          NullTestSupport.messageOf(exception)
              .contains("parent directory ACL must grant secret-directory access only"));
      assertFalse(NullTestSupport.messageOf(exception).contains(fileSystem.group.getName()));
    }
  }

  @Test
  void aclFilesystemRejectsKeyFilesInsideNonTraversableParentDirectories() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\non-traversable-parent.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      Objects.requireNonNull(keyPath.aclView)
          .setAcl(
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
          NullTestSupport.messageOf(exception)
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
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
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
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build()));
      keyPath.exists = true;
      keyPath.regularFile = true;
      Objects.requireNonNull(keyPath.aclView)
          .setAcl(
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
      assertRejectedKeyFilePath(
          keyPath,
          "path requires a parent directory.",
          () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
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
      assertRejectedKeyFilePath(
          keyPath,
          "path cannot use a parent path that already exists as a non-directory entry or symlink.",
          () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
    }
  }

  @Test
  void unsupportedFilesystemBranchesRejectWithoutNativeSecurityViews() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      AclFixturePath keyPath = fileSystem.path("\\keys\\unsupported.book-key");
      parentPath.exists = true;
      parentPath.regularFile = false;
      keyPath.exists = true;
      keyPath.regularFile = true;
      assertThrows(
          IllegalArgumentException.class,
          () -> SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(keyPath));
      assertThrows(
          SqliteCallerPathContractException.class,
          () -> SqliteOwnedRegularFileAccess.createNewEmptyFile(keyPath));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
    }
  }

  @Test
  void aclFilesystemWithoutAclViewRejectsDuringInspection() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build()));
      AclFixturePath keyPath = fileSystem.path("\\keys\\missing-view.book-key");
      keyPath.exists = true;
      keyPath.regularFile = true;
      keyPath.aclView = null;
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath).requireAccepted());
      assertTrue(
          NullTestSupport.messageOf(exception).contains("supports POSIX owner-only permissions"));
    }
  }

  private static void assertRejectedKeyFilePath(
      AclFixturePath expectedPath,
      String expectedMessage,
      org.junit.jupiter.api.function.Executable executable) {
    ContractFailureException exception = assertThrows(ContractFailureException.class, executable);
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, exception.failure().descriptor());
    assertEquals("The FinGrind book key file " + expectedMessage, exception.getMessage());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        Objects.requireNonNull(exception.failure().paths(), "path details").path());
  }
}
