package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies that backup-artifact sealing fails closed when its stage cannot make write progress. */
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
