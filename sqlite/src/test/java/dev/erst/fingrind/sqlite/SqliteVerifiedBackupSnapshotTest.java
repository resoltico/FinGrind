package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves an untransferred backup snapshot closes every resource while retaining its stage. */
class SqliteVerifiedBackupSnapshotTest {
  @TempDir Path tempDirectory;

  @Test
  void closesWithAndWithoutAnAttachedVerifiedBook() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
    assertDoesNotThrow(this::closeUnattachedSnapshot);
    assertDoesNotThrow(this::closeAttachedSnapshot);
  }

  private void closeUnattachedSnapshot() {
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(
            tempDirectory.resolve("unattached.sqlite"), ".snapshot-", ".tmp");
    try (SqliteVerifiedBackupSnapshot snapshot = new SqliteVerifiedBackupSnapshot(stage)) {
      snapshot.close();
    }
  }

  private void closeAttachedSnapshot() {
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(
            tempDirectory.resolve("attached.sqlite"), ".snapshot-", ".tmp");
    try (SqliteVerifiedBackupSnapshot snapshot = new SqliteVerifiedBackupSnapshot(stage)) {
      snapshot.attachBook(
          new SqliteVerifiedBook(
              tempDirectory.resolve("attached.sqlite"),
              SqliteBookPassphrase.fromUtf8Bytes(
                  "snapshot test", "test secret".getBytes(StandardCharsets.UTF_8))));
      snapshot.close();
    }
  }
}
