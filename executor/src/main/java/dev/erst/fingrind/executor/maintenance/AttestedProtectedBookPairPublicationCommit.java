package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.protocol.OperationId;
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
      case ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired
              prepublication ->
          throw AttestedProtectedBookMaintenanceDecisions.prepublicationRecoveryRequired(
              operation,
              prepublication.bookArtifactPath(),
              prepublication.secretArtifactPath(),
              prepublication.recoveryRecordState(),
              prepublication.pairPublicationRetention());
      case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
          throw AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
              blocked.bookArtifactPath(),
              blocked.bookArtifactState(),
              blocked.secretArtifactPath(),
              blocked.secretArtifactState(),
              blocked.pairPublicationRetention());
      case ProtectedBookPairPublicationFailureOutcome.CompletionUncertain uncertain ->
          throw AttestedProtectedBookMaintenanceDecisions.pairPublicationUncertain(
              operation,
              uncertain.bookArtifactPath(),
              uncertain.bookArtifactState(),
              uncertain.secretArtifactPath(),
              uncertain.secretArtifactState(),
              uncertain.pairPublicationRetention());
    };
  }
}
