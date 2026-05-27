package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for managed-library snapshot hardening and value-object rules. */
class SqliteManagedLibrarySecurityAndValueTest extends SqliteManagedLibraryIdentityTestSupport {
  @Test
  void hardenPrivateFile_returnsQuietlyWhenFileStoreMetadataCannotBeResolved() {
    Path missingPath = tempDirectory.resolve("missing-parent").resolve("missing.dylib");

    assertDoesNotThrow(() -> SqliteManagedLibraryIdentity.hardenPrivateFile(missingPath));
  }

  @Test
  void hardenPrivateDirectory_andFile_applyOwnerOnlyAclPermissions() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath directoryPath = fileSystem.path("\\snapshots");
      directoryPath.exists = true;
      directoryPath.regularFile = false;
      AclFixturePath libraryPath = fileSystem.path("\\snapshots\\sqlite3.dll");
      libraryPath.exists = true;
      libraryPath.regularFile = true;

      SqliteManagedLibraryIdentity.hardenPrivateDirectory(directoryPath);
      SqliteManagedLibraryIdentity.hardenPrivateFile(libraryPath);

      assertEquals(1, Objects.requireNonNull(directoryPath.aclView).getAcl().size());
      assertEquals(1, Objects.requireNonNull(libraryPath.aclView).getAcl().size());
      assertTrue(
          Objects.requireNonNull(libraryPath.aclView)
              .getAcl()
              .getFirst()
              .permissions()
              .contains(AclEntryPermission.READ_DATA));
      assertTrue(
          Objects.requireNonNull(libraryPath.aclView)
              .getAcl()
              .getFirst()
              .permissions()
              .contains(AclEntryPermission.EXECUTE));
    }
  }

  @Test
  void hardenPrivateDirectory_andFile_applyOwnerOnlyPosixPermissions() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath directoryPath = fileSystem.path("\\snapshots");
      directoryPath.exists = true;
      directoryPath.regularFile = false;
      directoryPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);
      AclFixturePath libraryPath = fileSystem.path("\\snapshots\\sqlite3.dylib");
      libraryPath.exists = true;
      libraryPath.regularFile = true;
      libraryPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);

      SqliteManagedLibraryIdentity.hardenPrivateDirectory(directoryPath);
      SqliteManagedLibraryIdentity.hardenPrivateFile(libraryPath);

      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          directoryPath.posixPermissions);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          libraryPath.posixPermissions);
    }
  }

  @Test
  void hardenPrivateFile_rejectsMissingAclViewsWhenAclSupportIsAdvertised() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath libraryPath = fileSystem.path("\\snapshots\\sqlite3.dll");
      libraryPath.exists = true;
      libraryPath.regularFile = true;
      libraryPath.aclView = null;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteManagedLibraryIdentity.hardenPrivateFile(libraryPath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Owner-only ACLs are unavailable"));
    }
  }

  @Test
  void hardenPrivateDirectory_wrapsAclIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath directoryPath = fileSystem.path("\\snapshots");
      directoryPath.exists = true;
      directoryPath.regularFile = false;
      directoryPath.overrideAclView = throwingAclView("boom");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteManagedLibraryIdentity.hardenPrivateDirectory(directoryPath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to apply private managed SQLite snapshot directory permissions"));
      assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void hardenPrivateDirectory_returnsQuietlyWhenOnlyBasicViewsAreAvailable() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath directoryPath = fileSystem.path("\\snapshots");
      directoryPath.exists = true;
      directoryPath.regularFile = false;

      assertDoesNotThrow(() -> SqliteManagedLibraryIdentity.hardenPrivateDirectory(directoryPath));
    }
  }

  @Test
  void hardenPrivateFile_wrapsPermissionFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath filePath = fileSystem.path("\\sqlite3.dll");
      filePath.exists = true;
      filePath.regularFile = true;
      filePath.overrideAclView = throwingAclView("boom");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteManagedLibraryIdentity.hardenPrivateFile(filePath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to apply private managed SQLite snapshot permissions"));
      assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_normalizesPathsAndDigests() {
    Path libraryPath = tempDirectory.resolve("library").resolve("..").resolve("libsqlite3.dylib");
    UnsupportedManagedSqliteLibraryIdentityException exception =
        new UnsupportedManagedSqliteLibraryIdentityException(
            libraryPath,
            " trusted resource ",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

    assertEquals(libraryPath.toAbsolutePath().normalize(), exception.libraryPath());
    assertEquals("trusted resource", exception.identitySource());
    assertEquals(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        exception.expectedSha256());
    assertEquals(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        exception.actualSha256());
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_rejectsInvalidDigests() {
    Path libraryPath = tempDirectory.resolve("libsqlite3.dylib");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new UnsupportedManagedSqliteLibraryIdentityException(
                    libraryPath,
                    "trusted resource",
                    "invalid",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("expectedSha256 must be one 64-character lowercase SHA-256 digest"));
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_rejectsBlankIdentitySources() {
    Path libraryPath = tempDirectory.resolve("libsqlite3.dylib");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new UnsupportedManagedSqliteLibraryIdentityException(
                    libraryPath,
                    "   ",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    assertEquals("identitySource must not be blank.", exception.getMessage());
  }
}
