package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused coverage for retained v3 directory-reservation control files. */
class SqliteMaintenanceLeaseArtifactsTest extends SqliteNativeBridgeTestSupport {
  @Test
  void directoryLeaseUsesOneDurableControlFileAndReleasesOnlyItsLock() throws Exception {
    Path parent = secureDirectory("directory-domain");
    Path canonicalParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
    Path controlPath = SqliteMaintenanceLeaseArtifacts.controlFilePath(canonicalParent);

    try (SqliteLeaseHandle heldLease =
        java.util.Objects.requireNonNull(
            SqliteMaintenanceLeaseArtifacts.acquire(canonicalParent), "initial lease")) {
      assertNotNull(heldLease);
      assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
      assertEqualsControlFileName(controlPath);
      assertTrue(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(canonicalParent));
      assertNull(SqliteMaintenanceLeaseArtifacts.acquire(canonicalParent));
    }
    assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
    assertFalse(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(canonicalParent));

    try (SqliteLeaseHandle reacquiredLease =
        java.util.Objects.requireNonNull(
            SqliteMaintenanceLeaseArtifacts.acquire(canonicalParent), "reacquired lease")) {
      assertNotNull(reacquiredLease);
    }
    assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredDirectoryLeaseResidueFailsClosedWithoutDeletion() throws Exception {
    Path parent = secureDirectory("retired-directory");
    Path retiredLease = parent.resolve(".fingrind-maintenance-directory-pid-999-start-0.lock");
    Files.writeString(retiredLease, "retired lease bytes");

    assertTrue(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(parent.toRealPath()));
    assertTrue(Files.exists(retiredLease, LinkOption.NOFOLLOW_LINKS));
    assertNull(SqliteMaintenanceLeaseArtifacts.acquire(parent.toRealPath()));
    assertTrue(Files.exists(retiredLease, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredV2DirectoryControlFailsClosedWithoutBeingReadOrAdopted() throws Exception {
    Path parent = secureDirectory("retired-v2-directory-control");
    Path retiredV2Control = parent.resolve(".fingrind-maintenance-directory-v2.control");
    Files.writeString(retiredV2Control, "retired v2 control contents");

    assertTrue(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(parent.toRealPath()));
    assertTrue(Files.exists(retiredV2Control, LinkOption.NOFOLLOW_LINKS));
    assertNull(SqliteMaintenanceLeaseArtifacts.acquire(parent.toRealPath()));
    assertTrue(Files.exists(retiredV2Control, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredRawTargetLeaseResidueFailsClosedWithoutDeletion() throws Exception {
    Path parent = secureDirectory("retired-raw");
    Path retired = parent.resolve("book.sqlite.fingrind-maintenance.lock");
    Files.writeString(retired, "retired lease bytes");

    assertTrue(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(parent.toRealPath()));
    assertTrue(Files.exists(retired, LinkOption.NOFOLLOW_LINKS));
    assertNull(SqliteMaintenanceLeaseArtifacts.acquire(parent.toRealPath()));
    assertTrue(Files.exists(retired, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void missingDirectoryHasNoLeaseArtifact() throws Exception {
    Path missing = tempDirectory.resolve("missing-directory");

    assertFalse(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(missing));
  }

  private Path secureDirectory(String name) throws java.io.IOException {
    Path directory = tempDirectory.resolve(name);
    Files.createDirectories(directory);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(directory);
    return directory;
  }

  private static void assertEqualsControlFileName(Path controlPath) {
    assertEquals(
        ".fingrind-maintenance-directory-v4.control", controlPath.getFileName().toString());
  }
}
