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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct cross-platform coverage for book-key filesystem security owners. */
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
  void createSecureEmptyFile_hardensPosixAndAclFiles() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\keys\\posix.book-key");

      SqliteBookKeyFileArtifactSecurity.createSecureEmptyFile(bookKeyFilePath);

      assertTrue(bookKeyFilePath.existsValue());
      assertTrue(bookKeyFilePath.regularFileValue());
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          bookKeyFilePath.posixPermissions);
    }
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\keys\\acl.book-key");

      SqliteBookKeyFileArtifactSecurity.createSecureEmptyFile(bookKeyFilePath);

      assertTrue(bookKeyFilePath.existsValue());
      assertTrue(bookKeyFilePath.regularFileValue());
      AclFixtureView aclView = Objects.requireNonNull(bookKeyFilePath.aclViewValue());
      List<AclEntry> acl = aclView.getAcl();
      assertEquals(1, acl.size());
      assertEquals(AclEntryType.ALLOW, acl.getFirst().type());
      assertEquals(fileSystem.owner(), acl.getFirst().principal());
      assertTrue(acl.getFirst().permissions().contains(AclEntryPermission.READ_DATA));
      assertTrue(acl.getFirst().permissions().contains(AclEntryPermission.WRITE_DATA));
    }
  }

  @Test
  void ensureSecureParentDirectory_andRequireSecureKeyFile_coverPosixAndAclHosts()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\private\\posix.book-key");
      Path parentDirectory = Objects.requireNonNull(bookKeyFilePath.getParent());

      SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath);
      SqliteBookKeyFileArtifactSecurity.createSecureEmptyFile(bookKeyFilePath);

      SqliteBookKeyFileArtifactSecurity.requireSecureKeyFile(bookKeyFilePath).requireAccepted();
      SqliteKeyFileSecurity parentSecurity =
          SqliteBookKeyFileDirectorySecurity.inspectSecurity(parentDirectory);

      SqlitePosixKeyFileSecurity posixSecurity =
          assertInstanceOf(SqlitePosixKeyFileSecurity.class, parentSecurity);
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          posixSecurity.permissions());
    }
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\private\\acl.book-key");
      Path parentDirectory = Objects.requireNonNull(bookKeyFilePath.getParent());

      SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(bookKeyFilePath);
      SqliteBookKeyFileArtifactSecurity.createSecureEmptyFile(bookKeyFilePath);

      SqliteBookKeyFileArtifactSecurity.requireSecureKeyFile(bookKeyFilePath).requireAccepted();
      SqliteKeyFileSecurity parentSecurity =
          SqliteBookKeyFileDirectorySecurity.inspectSecurity(parentDirectory);

      SqliteAclKeyFileSecurity aclSecurity =
          assertInstanceOf(SqliteAclKeyFileSecurity.class, parentSecurity);
      assertEquals(fileSystem.owner(), aclSecurity.owner());
      assertEquals(1, aclSecurity.acl().size());
      assertEquals(AclEntryType.ALLOW, aclSecurity.acl().getFirst().type());
      assertEquals(fileSystem.owner(), aclSecurity.acl().getFirst().principal());
      assertTrue(
          aclSecurity.acl().getFirst().permissions().contains(AclEntryPermission.LIST_DIRECTORY));
      assertTrue(aclSecurity.acl().getFirst().permissions().contains(AclEntryPermission.EXECUTE));
    }
  }

  @Test
  void aclFacadeCoverage_reportsAclDescriptorsAndRehardensOneDirectoryAndKeyFile()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookKeyFilePath = fileSystem.path("\\facade\\acl.book-key");
      AclFixturePath parentDirectory =
          assertInstanceOf(
              AclFixturePath.class, Objects.requireNonNull(bookKeyFilePath.getParent()));

      assertEquals(
          SqliteBookKeyFileSecurity.WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR,
          SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(bookKeyFilePath));
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(bookKeyFilePath);
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(bookKeyFilePath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(bookKeyFilePath);
      assertEquals(
          bookKeyFilePath,
          SqliteBookKeyFileSecurity.requireSecureKeyFile(bookKeyFilePath).requireAccepted());

      Objects.requireNonNull(parentDirectory.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));

      SqliteBookKeyFileSecurity.hardenDirectory(parentDirectory);

      List<AclEntry> hardenedAcl = Objects.requireNonNull(parentDirectory.aclViewValue()).getAcl();
      assertEquals(1, hardenedAcl.size());
      assertEquals(AclEntryType.ALLOW, hardenedAcl.getFirst().type());
      assertEquals(fileSystem.owner(), hardenedAcl.getFirst().principal());
      assertTrue(hardenedAcl.getFirst().permissions().contains(AclEntryPermission.LIST_DIRECTORY));
      assertTrue(hardenedAcl.getFirst().permissions().contains(AclEntryPermission.EXECUTE));
    }
  }

  @Test
  void callerPathSecurity_andKeyDirectoryHardening_coverPosixBranches() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(
              fileSystem.path("entity.sqlite")));

      AclFixturePath missingBookPath = fileSystem.path("\\missing\\entity.sqlite");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(missingBookPath));

      AclFixturePath nonSearchableBookParent = fileSystem.path("\\non-searchable");
      nonSearchableBookParent.exists = true;
      nonSearchableBookParent.regularFile = false;
      nonSearchableBookParent.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      AclFixturePath nonSearchableBookPath = fileSystem.path("\\non-searchable\\entity.sqlite");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(nonSearchableBookPath));

      AclFixturePath looseBookParent = fileSystem.path("\\books");
      looseBookParent.exists = true;
      looseBookParent.regularFile = false;
      looseBookParent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_EXECUTE);
      AclFixturePath looseBookPath = fileSystem.path("\\books\\entity.sqlite");
      assertEquals(
          Optional.of(looseBookParent),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(looseBookPath));
      assertEquals(OWNER_ONLY_DIRECTORY_PERMISSIONS, looseBookParent.posixPermissions);

      AclFixturePath secureBookParent = fileSystem.path("\\secure-books");
      secureBookParent.exists = true;
      secureBookParent.regularFile = false;
      secureBookParent.posixPermissions = OWNER_ONLY_DIRECTORY_PERMISSIONS;
      AclFixturePath secureBookPath = fileSystem.path("\\secure-books\\entity.sqlite");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(secureBookPath));

      AclFixturePath collidingBookParent = fileSystem.path("\\book-parent-file");
      collidingBookParent.exists = true;
      collidingBookParent.regularFile = true;
      AclFixturePath collidingBookPath = fileSystem.path("\\book-parent-file\\entity.sqlite");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(collidingBookPath));

      AclFixturePath secureKeyParent = fileSystem.path("\\secure-keys");
      secureKeyParent.exists = true;
      secureKeyParent.regularFile = false;
      secureKeyParent.posixPermissions = OWNER_ONLY_DIRECTORY_PERMISSIONS;
      assertFalse(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              secureKeyParent));

      AclFixturePath looseKeyParent = fileSystem.path("\\loose-keys");
      looseKeyParent.exists = true;
      looseKeyParent.regularFile = false;
      looseKeyParent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ);
      assertTrue(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              looseKeyParent));
      assertEquals(OWNER_ONLY_DIRECTORY_PERMISSIONS, looseKeyParent.posixPermissions);

      AclFixturePath nonExecutableKeyParent = fileSystem.path("\\non-executable-keys");
      nonExecutableKeyParent.exists = true;
      nonExecutableKeyParent.regularFile = false;
      nonExecutableKeyParent.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      assertFalse(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              nonExecutableKeyParent));

      AclFixturePath callerLooseKeyParent = fileSystem.path("\\caller-loose-keys");
      callerLooseKeyParent.exists = true;
      callerLooseKeyParent.regularFile = false;
      callerLooseKeyParent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_EXECUTE);
      AclFixturePath callerLooseKeyPath = fileSystem.path("\\caller-loose-keys\\entity.book-key");
      assertEquals(
          Optional.of(callerLooseKeyParent),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(callerLooseKeyPath));
      assertEquals(OWNER_ONLY_DIRECTORY_PERMISSIONS, callerLooseKeyParent.posixPermissions);

      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(
              fileSystem.path("entity.book-key")));

      AclFixturePath missingKeyPath = fileSystem.path("\\missing-keys\\entity.book-key");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(missingKeyPath));

      AclFixturePath collidingKeyParent = fileSystem.path("\\key-parent-file");
      collidingKeyParent.exists = true;
      collidingKeyParent.regularFile = true;
      AclFixturePath collidingKeyPath = fileSystem.path("\\key-parent-file\\entity.book-key");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(collidingKeyPath));

      AclFixturePath nonDirectory = fileSystem.path("\\not-a-directory");
      nonDirectory.exists = true;
      nonDirectory.regularFile = true;
      assertFalse(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(nonDirectory));
    }
  }

  @Test
  void callerPathSecurity_andKeyDirectoryHardening_coverAclAndUnsupportedBranches()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath noTraverseAclParent = fileSystem.path("\\acl-no-traverse");
      noTraverseAclParent.exists = true;
      noTraverseAclParent.regularFile = false;
      Objects.requireNonNull(noTraverseAclParent.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      assertFalse(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              noTraverseAclParent));

      AclFixturePath ownerOnlyAclParent = fileSystem.path("\\acl-owner-only");
      ownerOnlyAclParent.exists = true;
      ownerOnlyAclParent.regularFile = false;
      Objects.requireNonNull(ownerOnlyAclParent.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                      .build()));
      assertFalse(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              ownerOnlyAclParent));
      AclFixturePath ownerOnlyKeyPath = fileSystem.path("\\acl-owner-only\\entity.book-key");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(ownerOnlyKeyPath));

      AclFixturePath sharedAclParent = fileSystem.path("\\acl-shared");
      sharedAclParent.exists = true;
      sharedAclParent.regularFile = false;
      Objects.requireNonNull(sharedAclParent.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.DENY)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      assertTrue(
          SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
              sharedAclParent));
      List<AclEntry> hardenedAcl = Objects.requireNonNull(sharedAclParent.aclViewValue()).getAcl();
      assertEquals(1, hardenedAcl.size());
      assertEquals(fileSystem.owner(), hardenedAcl.getFirst().principal());

      AclFixturePath callerSharedAclParent = fileSystem.path("\\acl-caller-shared");
      callerSharedAclParent.exists = true;
      callerSharedAclParent.regularFile = false;
      Objects.requireNonNull(callerSharedAclParent.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.EXECUTE)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      AclFixturePath callerSharedKeyPath = fileSystem.path("\\acl-caller-shared\\entity.book-key");
      assertEquals(
          Optional.of(callerSharedAclParent),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(callerSharedKeyPath));

      AclFixturePath sharedBookParent = fileSystem.path("\\acl-books");
      sharedBookParent.exists = true;
      sharedBookParent.regularFile = false;
      Objects.requireNonNull(sharedBookParent.aclViewValue())
          .setAcl(
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.LIST_DIRECTORY)
                      .build()));
      AclFixturePath sharedBookPath = fileSystem.path("\\acl-books\\entity.sqlite");
      assertEquals(
          Optional.of(sharedBookParent),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(sharedBookPath));
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of())) {
      AclFixturePath unsupportedBookParent = fileSystem.path("\\unsupported-books");
      unsupportedBookParent.exists = true;
      unsupportedBookParent.regularFile = false;
      AclFixturePath unsupportedBookPath = fileSystem.path("\\unsupported-books\\entity.sqlite");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookParentDirectory(unsupportedBookPath));

      AclFixturePath unsupportedKeyParent = fileSystem.path("\\unsupported-keys");
      unsupportedKeyParent.exists = true;
      unsupportedKeyParent.regularFile = false;
      AclFixturePath unsupportedKeyPath = fileSystem.path("\\unsupported-keys\\entity.book-key");
      assertEquals(
          Optional.empty(),
          SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(unsupportedKeyPath));
    }
  }
}
