package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Behavioural coverage for exact object-control namespace admission and ownership. */
class SqliteObjectCoordinationArtifactsCoverageTest extends SqliteNativeBridgeTestSupport {
  @Test
  void objectCoordinationRejectsAControlNameWithANonHexDigest() throws Exception {
    Path root = privateDirectory("invalid-control-name");
    Path artifact =
        SqliteTestPrivateDirectorySupport.writeOwnerOnlyUtf8File(
            tempDirectory.resolve("invalid-control-name.sqlite"), "book");

    try (AutoCloseable ignoredRoot = SqliteObjectCoordinationRoot.installTestRoot(root)) {
      Files.writeString(
          root.resolve("object-v4-" + "g".repeat(64) + ".control"), "invalid control");

      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(artifact));

      assertTrue(
          java.util.Objects.requireNonNull(exception.getMessage(), "coordination failure message")
              .contains("Unexpected state"));
    }
  }

  @Test
  void objectCoordinationRejectsAnUppercaseControlDigest() throws Exception {
    Path root = privateDirectory("uppercase-control-name");
    Path artifact =
        SqliteTestPrivateDirectorySupport.writeOwnerOnlyUtf8File(
            tempDirectory.resolve("uppercase-control-name.sqlite"), "book");

    try (AutoCloseable ignoredRoot = SqliteObjectCoordinationRoot.installTestRoot(root)) {
      Files.writeString(
          root.resolve("object-v4-" + "a".repeat(63) + "A.control"), "invalid control");

      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(artifact));

      assertTrue(
          java.util.Objects.requireNonNull(exception.getMessage(), "coordination failure message")
              .contains("Unexpected state"));
    }
  }

  @Test
  void objectCoordinationRejectsAControlDigestBelowTheHexRange() throws Exception {
    Path root = privateDirectory("punctuation-control-name");
    Path artifact =
        SqliteTestPrivateDirectorySupport.writeOwnerOnlyUtf8File(
            tempDirectory.resolve("punctuation-control-name.sqlite"), "book");

    try (AutoCloseable ignoredRoot = SqliteObjectCoordinationRoot.installTestRoot(root)) {
      Files.writeString(
          root.resolve("object-v4-" + "a".repeat(63) + "!.control"), "invalid control");

      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(artifact));

      assertTrue(
          java.util.Objects.requireNonNull(exception.getMessage(), "coordination failure message")
              .contains("Unexpected state"));
    }
  }

  @Test
  void objectCoordinationRetainsOneExclusiveMaintenanceControlForAnArtifact() throws Exception {
    Path root = privateDirectory("exclusive-maintenance-control");
    Path artifact =
        SqliteTestPrivateDirectorySupport.writeOwnerOnlyUtf8File(
            tempDirectory.resolve("exclusive-maintenance-control.sqlite"), "book");

    try (AutoCloseable ignoredRoot = SqliteObjectCoordinationRoot.installTestRoot(root);
        SqliteLeaseHandle ignored =
            java.util.Objects.requireNonNull(
                SqliteObjectCoordinationArtifacts.tryAcquireMaintenanceExclusion(artifact),
                "initial maintenance exclusion")) {
      assertNull(
          SqliteObjectCoordinationArtifacts.tryAcquireMaintenanceExclusion(artifact),
          "The held maintenance exclusion should block a second acquisition");
    }
  }

  private Path privateDirectory(String name) throws IOException {
    Path directory = Files.createDirectory(tempDirectory.resolve(name));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(directory);
    return directory;
  }
}
