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
  void hardenBookArtifacts_skipsNonDirectoryParentsAndRejectsMissingAclViews() throws Exception {
    Path missingParentBookPath = tempDirectory.resolve("missing-parent").resolve("book.sqlite");
    assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenBookArtifacts(missingParentBookPath));
    Path parentFile = tempDirectory.resolve("parent-file");
    Files.writeString(parentFile, "not-a-directory");
    Path nestedBookPath = parentFile.resolve("book.sqlite");
    assertDoesNotThrow(() -> SqliteBookFileSecurity.hardenBookArtifacts(nestedBookPath));
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
}
