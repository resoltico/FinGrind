package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies that backup-artifact snapshot and sealing I/O failures retain their private stage. */
class SqliteStagedBackupArtifactTest {

  @Test
  void sealRetainsTheStageWhenItsSecureWriteMakesNoProgress() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\backup");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath finalPath = fileSystem.path("\\backup\\artifact.sqlite");
      AclFixturePath stagePath = fileSystem.path("\\backup\\artifact.stage");
      stagePath.exists = true;
      stagePath.regularFile = true;
      stagePath.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      stagePath.replaceContent(new byte[] {1, 2});

      SqliteOwnedStagedArtifact ownedStage =
          SqliteOwnedStagedArtifact.recordExisting(finalPath, stagePath);
      try {
        SqliteStagedBackupArtifact unreadableArtifact =
            new SqliteStagedBackupArtifact(
                ownedStage,
                finalPath,
                ignored -> {
                  throw new java.io.IOException("simulated staged snapshot read failure");
                },
                (ignoredPath, ignoredArtifact) -> {
                  throw new AssertionError("The failed snapshot must not proceed to sealing.");
                });

        IllegalStateException snapshotFailure =
            assertThrows(IllegalStateException.class, unreadableArtifact::snapshot);

        assertEquals(
            "Failed to read the staged encrypted backup snapshot.", snapshotFailure.getMessage());
        assertEquals(
            "simulated staged snapshot read failure",
            Objects.requireNonNull(snapshotFailure.getCause(), "snapshot failure cause")
                .getMessage());

        stagePath.returnZeroProgressFromNextWrite();
        SqliteStagedBackupArtifact artifact = new SqliteStagedBackupArtifact(ownedStage, finalPath);

        IllegalStateException failure =
            assertThrows(IllegalStateException.class, () -> artifact.seal(new byte[] {1, 2, 3}));

        assertEquals("Failed to seal the staged attested backup artifact.", failure.getMessage());
        assertEquals(
            "Failed to write the complete staged attested backup artifact.",
            Objects.requireNonNull(failure.getCause(), "seal failure cause").getMessage());
      } finally {
        ownedStage.releaseRetained();
      }
    }
  }
}
