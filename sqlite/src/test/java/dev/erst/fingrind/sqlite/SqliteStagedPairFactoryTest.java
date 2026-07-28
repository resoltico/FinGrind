package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies that pair factories retain typed witness failures at their staging boundary. */
class SqliteStagedPairFactoryTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void pairFactoriesRejectRetiredCapabilityProbeResidueBeforeOwningPublicationResources()
      throws Exception {
    SqliteStagedProtectedBookPairArtifacts restoredArtifacts = artifacts("restored");
    SqliteStagedProtectedBookPairArtifacts backupArtifacts = artifacts("backup");
    Files.writeString(tempDirectory.resolve(".fingrind-book-no-replace-probe-abandoned"), "retired");

    IllegalStateException restoredFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteStagedRestoredBookPairFactory.create(
                    restoredArtifacts,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    "restored passphrase".getBytes(StandardCharsets.UTF_8),
                    VERIFICATION_SUPPORT,
                    SqliteRestoredBookPairPublication.defaultOperators()));
    IllegalStateException backupFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteStagedBackupPairFactory.create(
                    backupArtifacts,
                    "backup passphrase".getBytes(StandardCharsets.UTF_8),
                    VERIFICATION_SUPPORT,
                    Files::createLink,
                    Files::createLink,
                    null,
                    null,
                    (ignoredStep, ignoredParent) -> {}));

    assertTrue(
        java.util.Objects.requireNonNull(restoredFailure.getMessage(), "restored failure message")
            .contains("capability witnesses"));
    assertTrue(
        java.util.Objects.requireNonNull(backupFailure.getMessage(), "backup failure message")
            .contains("capability witnesses"));
  }

  private SqliteStagedProtectedBookPairArtifacts artifacts(String prefix) throws Exception {
    Path bookTarget = tempDirectory.resolve(prefix + ".sqlite");
    Path secretTarget = tempDirectory.resolve(prefix + ".key");
    Path bookStage = Files.writeString(tempDirectory.resolve(prefix + ".sqlite.stage"), "book");
    Path secretStage = Files.writeString(tempDirectory.resolve(prefix + ".key.stage"), "secret");
    return new SqliteStagedProtectedBookPairArtifacts(
        SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage),
        bookTarget,
        SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage),
        secretTarget);
  }
}
