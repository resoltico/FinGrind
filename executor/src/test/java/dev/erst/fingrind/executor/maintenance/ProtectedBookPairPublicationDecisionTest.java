package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises every publish-or-preserve branch of retained protected-book pair evidence. */
class ProtectedBookPairPublicationDecisionTest {
  private static final Path BOOK = Path.of("pair-decisions", "book.sqlite");
  private static final Path KEY = Path.of("pair-decisions", "book.key");

  @Test
  void acceptsOnlyThePublishedPairCommitOutcome() {
    StagedPairPublicationCommitOutcome.Published published =
        new StagedPairPublicationCommitOutcome.Published(retention());

    assertEquals(
        published,
        AttestedProtectedBookPairPublicationCommit.requirePublished(
            OperationId.BACKUP_BOOK, published));
  }

  @Test
  void projectsEveryPreservedPairStateToItsPublicContractFailure() {
    ContractFailureException prepublication =
        assertThrows(
            ContractFailureException.class,
            () ->
                AttestedProtectedBookPairPublicationCommit.requirePublished(
                    OperationId.RESTORE_BOOK,
                    new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
                        BOOK,
                        KEY,
                        ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                        retention())));
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        prepublication.failure().descriptor());

    ContractFailureException blocked =
        assertThrows(
            ContractFailureException.class,
            () ->
                AttestedProtectedBookPairPublicationCommit.requirePublished(
                    OperationId.BACKUP_BOOK,
                    new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                        BOOK,
                        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                        KEY,
                        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                        null)));
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        blocked.failure().descriptor());

    ContractFailureException uncertain =
        assertThrows(
            ContractFailureException.class,
            () ->
                AttestedProtectedBookPairPublicationCommit.requirePublished(
                    OperationId.REKEY_BOOK,
                    new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                        BOOK,
                        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                        KEY,
                        ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                        retention())));
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        uncertain.failure().descriptor());
  }

  @Test
  void decisionFactoriesKeepTheirTypedOutcomesAndPublicFailureDescriptors() {
    ProtectedBookMaintenanceRejection rejection =
        new ProtectedBookMaintenanceRejection.PairTargetsConflict(BOOK, KEY);

    assertInstanceOf(
        ProtectedBookBackupOutcome.Rejected.class,
        assertInstanceOf(
                MaintenanceDecision.Accepted.class,
                AttestedProtectedBookMaintenanceDecisions.rejectedBackup(rejection))
            .value());
    assertInstanceOf(
        ProtectedBookRestoreOutcome.Rejected.class,
        assertInstanceOf(
                MaintenanceDecision.Accepted.class,
                AttestedProtectedBookMaintenanceDecisions.rejectedRestore(rejection))
            .value());
    assertInstanceOf(
        ProtectedBookRekeyOutcome.Rejected.class,
        assertInstanceOf(
                MaintenanceDecision.Accepted.class,
                AttestedProtectedBookMaintenanceDecisions.rejectedRekey(rejection))
            .value());
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
        assertInstanceOf(
                MaintenanceDecision.Failed.class,
                AttestedProtectedBookMaintenanceDecisions.failure(
                    BOOK, "bookFilePath", "storage unavailable"))
            .failure()
            .toContractFailure()
            .descriptor());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        AttestedProtectedBookMaintenanceDecisions.pairPublicationUncertain(
                OperationId.BACKUP_BOOK,
                BOOK,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                KEY,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                null)
            .failure()
            .descriptor());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        AttestedProtectedBookMaintenanceDecisions.prepublicationRecoveryRequired(
                OperationId.RESTORE_BOOK,
                BOOK,
                KEY,
                ProtectedBookPairPublicationRecoveryRecordState.DURABILITY_UNCONFIRMED,
                retention())
            .failure()
            .descriptor());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
                BOOK,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                KEY,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                null)
            .failure()
            .descriptor());
  }

  private static ProtectedBookPairPublicationRetention retention() {
    return new ProtectedBookPairPublicationRetention(
        publication(BOOK, ".book-stage"), publication(KEY, ".key-stage"));
  }

  private static ArtifactPublicationResult publication(Path published, String stageName) {
    Path normalizedPublished = published.toAbsolutePath().normalize();
    return ArtifactPublicationResult.restoreCapturedCanonicalPaths(
        normalizedPublished,
        new ArtifactPublicationRetention(normalizedPublished.resolveSibling(stageName)));
  }
}
