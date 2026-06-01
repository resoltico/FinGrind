package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

/** Direct cross-platform coverage for book-key filesystem security owners. */
class SqliteBookKeyFileFilesystemSecurityCoverageTest {
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
}
