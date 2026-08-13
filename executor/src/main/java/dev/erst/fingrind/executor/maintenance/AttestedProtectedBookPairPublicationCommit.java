package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.util.Objects;

/** Maps a staged pair-commit result to its one publish-or-preserve maintenance decision. */
final class AttestedProtectedBookPairPublicationCommit {
  private AttestedProtectedBookPairPublicationCommit() {}

  static StagedPairPublicationCommitOutcome.Published requirePublished(
      OperationId operation, StagedPairPublicationCommitOutcome outcome) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case StagedPairPublicationCommitOutcome.Published published -> published;
      case StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete incomplete ->
          throw new ContractFailureException(
              ContractErrors.publicationTransactionIncompleteFailure(
                  incomplete.candidateArtifactPath(),
                  incomplete.transactionResult(),
                  publicationArgument(operation)));
      case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
          throw AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
              blocked.bookArtifactPath(),
              blocked.bookArtifactState(),
              blocked.secretArtifactPath(),
              blocked.secretArtifactState());
    };
  }

  /** Raises the transaction-specific safe failure selected during pair-admission recovery. */
  static ContractFailureException incompleteAdmission(
      OperationId operation,
      dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission
              .PublicationTransactionIncomplete
          incomplete) {
    var checkedIncomplete = Objects.requireNonNull(incomplete, "incomplete");
    return new ContractFailureException(
        ContractErrors.publicationTransactionIncompleteFailure(
            checkedIncomplete.candidateArtifactPath(),
            checkedIncomplete.transactionResult(),
            publicationArgument(Objects.requireNonNull(operation, "operation"))));
  }

  private static String publicationArgument(OperationId operation) {
    return switch (Objects.requireNonNull(operation, "operation")) {
      case BACKUP_BOOK -> "backupFilePath";
      case RESTORE_BOOK, REKEY_BOOK -> "bookFilePath";
      default ->
          throw new IllegalArgumentException(
              "Only protected-book maintenance operations can select a pair-publication argument.");
    };
  }
}
