package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves that pair publication rejects an insecure secret filesystem before staging output. */
class SqliteStagedProtectedBookPairCapabilityTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void stagedPairs_refuseAnInsecureSecretFilesystemBeforeStaging() throws Exception {
    Path zipArchive = tempDirectory.resolve("no-link.zip");
    try (FileSystem fileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + zipArchive.toUri()), Map.of("create", "true"))) {
      Path stagedSecretPath = Files.writeString(fileSystem.getPath("/backup-stage.key"), "key");
      Path finalSecretPath = fileSystem.getPath("/backup.key");

      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteOwnedStagedArtifact.recordExisting(finalSecretPath, stagedSecretPath));

      assertEquals(SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED, failure.pathFailure());
      assertTrue(Files.exists(stagedSecretPath));
      assertFalse(Files.exists(finalSecretPath));
    }
  }
}
