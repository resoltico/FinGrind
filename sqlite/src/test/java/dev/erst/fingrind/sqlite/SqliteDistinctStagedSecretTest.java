package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves distinct-secret selection retains every rejected owner-owned candidate. */
class SqliteDistinctStagedSecretTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void generateTransfersTheFirstDistinctStageAndAnIndependentSecretCopy() throws Exception {
    Path target = tempDirectory.resolve("distinct-target.key");
    AtomicInteger checkpoints = new AtomicInteger();
    List<SqliteOwnedStagedArtifact> stages = new ArrayList<>();

    try (SqliteBookPassphrase source = passphrase("source secret")) {
      SqliteDistinctStagedSecret.GeneratedSecret generated =
          SqliteDistinctStagedSecret.generate(
              stageCreator(target, stages),
              source,
              SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
              ignored -> checkpoints.incrementAndGet(),
              stagedPath -> writeSecret(stagedPath, "different secret"));
      try (SqliteBookPassphrase generatedPassphrase = generated.passphrase()) {
        assertFalse(generatedPassphrase.hasSameSecretAs(source));
        assertEquals(generated.stagedSecretFile(), stages.getFirst());
        assertTrue(Files.exists(generated.stagedSecretFile().stagedPath()));
      } finally {
        generated.stagedSecretFile().releaseRetained();
      }
    }

    assertEquals(1, checkpoints.get());
  }

  @Test
  void generateRetainsEveryDuplicateCandidateBeforeReportingExhaustion() throws Exception {
    Path target = tempDirectory.resolve("duplicate-target.key");
    AtomicInteger checkpoints = new AtomicInteger();
    List<SqliteOwnedStagedArtifact> stages = new ArrayList<>();

    try (SqliteBookPassphrase source = passphrase("same secret")) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteDistinctStagedSecret.generate(
                      stageCreator(target, stages),
                      source,
                      SqliteProtectedBookStagingCheckpoint.RESTORE_SECRET_GENERATION,
                      ignored -> checkpoints.incrementAndGet(),
                      stagedPath -> writeSecret(stagedPath, "same secret")));

      assertTrue(
          java.util.Objects.requireNonNull(failure.getMessage(), "failure message")
              .contains("Unable to generate a distinct"));
    }

    assertEquals(32, checkpoints.get());
    assertEquals(32, stages.size());
    assertTrue(stages.stream().allMatch(stage -> Files.exists(stage.stagedPath())));
  }

  private static SqliteBookPassphrase passphrase(String secret) {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "test source", secret.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeSecret(Path stagedPath, String secret) {
    try {
      Files.writeString(stagedPath, secret, StandardCharsets.UTF_8);
    } catch (java.io.IOException exception) {
      throw new AssertionError("Unable to populate one owned test stage.", exception);
    }
  }

  private static SqliteDistinctStagedSecret.StageCreator stageCreator(
      Path target, List<SqliteOwnedStagedArtifact> stages) {
    return () -> {
      SqliteOwnedStagedArtifact stage =
          SqliteOwnedStagedArtifact.create(target, ".distinct-secret-", ".tmp");
      stages.add(stage);
      return stage;
    };
  }
}
