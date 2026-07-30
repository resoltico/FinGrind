package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies the durable boundary rechecks staged bytes after its evidence is force-confirmed. */
class SqlitePairPublicationDurabilityTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void recoveredBoundaryRejectsAStageChangedAfterItsImmutableEvidenceWasRecorded()
      throws Exception {
    Path bookTarget = tempDirectory.resolve("changed-stage/book.sqlite");
    Path secretTarget = tempDirectory.resolve("changed-stage/book.key");
    Path bookStage = writeArtifact("changed-stage/.book.stage", "book stage");
    Path secretStage = writeArtifact("changed-stage/.secret.stage", "secret stage");
    SqliteOwnedStagedArtifact ownedBookStage =
        SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact ownedSecretStage =
        SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    SqliteProtectedBookPairPublicationRecord record =
        SqliteProtectedBookPairPublicationRecord.create(
            bookTarget,
            secretTarget,
            bookStage,
            secretStage,
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupBinding(bookTarget.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    Files.writeString(secretStage, "changed secret stage");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationDurability.forceAndRequireRecoveryBoundary(
                    record,
                    ownedSecretStage,
                    secretTarget,
                    false,
                    (ignoredStep, ignoredParent) -> {},
                    SqliteOwnedRegularFileAccess::forceFile));

    assertEquals(
        "The staged protected-book pair member changed after durable recovery evidence was recorded.",
        failure.getMessage());
    ownedBookStage.releaseRetained();
    ownedSecretStage.releaseRetained();
  }
}
