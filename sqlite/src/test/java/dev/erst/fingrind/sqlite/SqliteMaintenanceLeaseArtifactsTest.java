package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused coverage for maintenance-lease artifact helpers. */
class SqliteMaintenanceLeaseArtifactsTest extends SqliteNativeBridgeTestSupport {
  @Test
  void acquire_returnsNullAfterRepeatedCreateCollisions() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath currentLeasePath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-maintenance-"
                  + SqliteProcessIdentity.current().coordinationToken()
                  + ".lock");
      FileAlreadyExistsException createCollision =
          new FileAlreadyExistsException(currentLeasePath.toString());
      for (int attempt = 0; attempt < 8; attempt++) {
        currentLeasePath.failCreateDirectoryWith(createCollision);
      }

      assertNull(SqliteMaintenanceLeaseArtifacts.acquire(artifactPath));
    }
  }

  @Test
  void hasLiveArtifact_returnsFalseWithoutOneDirectoryParentAndWhenLegacyLeaseDisappears()
      throws Exception {
    assertFalse(
        SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(java.nio.file.Path.of("book.sqlite")));

    Path cleanArtifactPath = writeArtifact("no-lease-siblings.sqlite");
    assertFalse(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(cleanArtifactPath));

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = true;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;

      assertFalse(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));

      parentPath.regularFile = false;
      AclFixturePath legacyLeasePath =
          fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      legacyLeasePath.exists = true;
      legacyLeasePath.regularFile = true;
      legacyLeasePath.failNewByteChannelWith(new NoSuchFileException(legacyLeasePath.toString()));

      assertFalse(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));
      assertFalse(legacyLeasePath.exists);
    }
  }

  @Test
  void hasLiveArtifact_treatsUndeletableInvalidAndStaleSiblingDirectoriesAsLive()
      throws IOException {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;

      AclFixturePath invalidLeasePath =
          fileSystem.path("\\books\\book.sqlite.fingrind-maintenance-invalid.lock");
      invalidLeasePath.exists = true;
      invalidLeasePath.regularFile = false;
      invalidLeasePath.preserveExistingEntryOnDeleteIfExists();

      assertTrue(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));

      invalidLeasePath.exists = false;
      AclFixturePath staleLeasePath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-maintenance-"
                  + SqliteProcessIdentity.coordinationToken(
                      999_999_999L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)
                  + ".lock");
      staleLeasePath.exists = true;
      staleLeasePath.regularFile = false;
      staleLeasePath.preserveExistingEntryOnDeleteIfExists();

      assertTrue(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));
    }
  }

  @Test
  void hasLiveArtifact_ignoresWrongSuffixAndMatchingRegularFileSiblings() throws Exception {
    Path artifactPath = writeArtifact("scan.sqlite");
    Path parentPath = java.util.Objects.requireNonNull(artifactPath.getParent(), "parentPath");
    Path wrongSuffixSibling =
        parentPath.resolve(
            artifactPath.getFileName()
                + ".fingrind-maintenance-"
                + SqliteProcessIdentity.current().coordinationToken()
                + ".tmp");
    Files.writeString(wrongSuffixSibling, "wrong-suffix");
    Path regularFileSibling =
        parentPath.resolve(
            artifactPath.getFileName()
                + ".fingrind-maintenance-"
                + SqliteProcessIdentity.coordinationToken(
                    999_999_999L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)
                + ".lock");
    Files.writeString(regularFileSibling, "not-a-directory");

    assertFalse(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));
    assertTrue(Files.exists(wrongSuffixSibling));
    assertTrue(Files.exists(regularFileSibling));
  }

  @Test
  void hasLiveArtifact_deletesInvalidAndStaleSiblingDirectoriesWhenTheyAreReclaimable()
      throws Exception {
    Path artifactPath = writeArtifact("reclaimable.sqlite");
    Path parentPath = java.util.Objects.requireNonNull(artifactPath.getParent(), "parentPath");
    Path invalidSibling =
        parentPath.resolve(artifactPath.getFileName() + ".fingrind-maintenance-invalid.lock");
    Files.createDirectory(invalidSibling);
    Path staleSibling =
        parentPath.resolve(
            artifactPath.getFileName()
                + ".fingrind-maintenance-"
                + SqliteProcessIdentity.coordinationToken(
                    999_999_999L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)
                + ".lock");
    Files.createDirectory(staleSibling);

    assertFalse(SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));
    assertFalse(Files.exists(invalidSibling));
    assertFalse(Files.exists(staleSibling));
  }

  @Test
  void hasLiveArtifact_wrapsDirectoryStreamCloseFailure() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failDirectoryStreamCloseWith(new IOException("close-boom"));
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;

      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(artifactPath));
      assertTrue(NullTestSupport.messageOf(exception).contains("close-boom"));
    }
  }

  private Path writeArtifact(String fileName) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Files.writeString(artifactPath, "content");
    return artifactPath.toAbsolutePath().normalize();
  }
}
