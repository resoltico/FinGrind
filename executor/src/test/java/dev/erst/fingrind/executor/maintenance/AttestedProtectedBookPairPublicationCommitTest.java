package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.PublicationTransactionTestFixtures;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pins the non-retryable pair-commit translation shared by backup, restore, and rekey. */
@SuppressWarnings("NullAway")
class AttestedProtectedBookPairPublicationCommitTest {
  private static final Path BOOK_TARGET = Path.of("target/book.sqlite");
  private static final Path SECRET_TARGET = Path.of("target/book.key");

  @Test
  void returnsOnlyPublishedPairOutcomes() {
    StagedPairPublicationCommitOutcome.Published published =
        new StagedPairPublicationCommitOutcome.Published(
            new dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication(
                PublicationTransactionTestFixtures.completedArtifact(BOOK_TARGET),
                PublicationTransactionTestFixtures.completedArtifact(SECRET_TARGET)));

    assertSame(
        published,
        AttestedProtectedBookPairPublicationCommit.requirePublished(
            OperationId.BACKUP_BOOK, published));
    assertThrows(
        NullPointerException.class,
        () ->
            AttestedProtectedBookPairPublicationCommit.requirePublished(
                OperationId.BACKUP_BOOK, null));
  }

  @Test
  void translatesEveryIncompleteOperationAndFailsClosedForBlockedEvidence() {
    StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete incomplete =
        new StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete(
            BOOK_TARGET, PublicationTransactionTestFixtures.incompleteResult());
    ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete admission =
        new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
            BOOK_TARGET, PublicationTransactionTestFixtures.incompleteResult());
    for (OperationId operation :
        new OperationId[] {
          OperationId.BACKUP_BOOK, OperationId.RESTORE_BOOK, OperationId.REKEY_BOOK
        }) {
      ContractFailureException commitFailure =
          assertThrows(
              ContractFailureException.class,
              () ->
                  AttestedProtectedBookPairPublicationCommit.requirePublished(
                      operation, incomplete));
      assertEquals(
          ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
          commitFailure.failure().descriptor());

      ContractFailureException admissionFailure =
          AttestedProtectedBookPairPublicationCommit.incompleteAdmission(operation, admission);
      assertEquals(
          ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
          admissionFailure.failure().descriptor());
    }

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked =
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
            BOOK_TARGET,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            SECRET_TARGET,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED);
    ContractFailureException blockedFailure =
        assertThrows(
            ContractFailureException.class,
            () ->
                AttestedProtectedBookPairPublicationCommit.requirePublished(
                    OperationId.BACKUP_BOOK, blocked));
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        blockedFailure.failure().descriptor());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestedProtectedBookPairPublicationCommit.incompleteAdmission(
                OperationId.HELP,
                new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
                    BOOK_TARGET, PublicationTransactionTestFixtures.incompleteResult())));
  }
}
