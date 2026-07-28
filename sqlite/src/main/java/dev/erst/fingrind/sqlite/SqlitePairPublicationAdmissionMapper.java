package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.nio.file.Path;
import java.util.Objects;

/** Maps a recovered pair-publication fact to the corresponding public admission result. */
final class SqlitePairPublicationAdmissionMapper {
  private SqlitePairPublicationAdmissionMapper() {}

  static ProtectedBookPairPublicationAdmission fromRecoveredReconciliation(
      SqlitePairPublicationReconciliation reconciliation) {
    SqlitePairPublicationReconciliation checkedReconciliation =
        Objects.requireNonNull(reconciliation, "reconciliation");
    if (checkedReconciliation instanceof SqlitePairPublicationReconciliationRecovered recovered) {
      return new ProtectedBookPairPublicationAdmission.Recovered(
          recovered.binding(), recovered.retention());
    }
    if (checkedReconciliation
        instanceof SqlitePairPublicationReconciliationExistingCompleteBackup existing) {
      return new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
          existing.backupArtifactPath(), existing.backupKeyPath());
    }
    if (checkedReconciliation
        instanceof SqlitePairPublicationReconciliationPrepublicationRecoveryRequired prepublication) {
      return new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
          prepublication.bookArtifactPath(),
          prepublication.secretArtifactPath(),
          prepublication.recoveryRecordState(),
          prepublication.pairPublicationRetention());
    }
    if (checkedReconciliation
        instanceof SqlitePairPublicationReconciliationEvidenceBlocked blocked) {
      return new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
          blocked.bookArtifactPath(),
          blocked.bookArtifactState(),
          blocked.secretArtifactPath(),
          blocked.secretArtifactState(),
          blocked.pairPublicationRetention());
    }
    if (checkedReconciliation
        instanceof SqlitePairPublicationReconciliationCompletionUncertain uncertain) {
      return new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
          uncertain.bookArtifactPath(),
          uncertain.bookArtifactState(),
          uncertain.secretArtifactPath(),
          uncertain.secretArtifactState(),
          uncertain.pairPublicationRetention());
    }
    throw new IllegalArgumentException(
        "A new pair-publication admission cannot map an absent reconciliation.");
  }

  static ProtectedBookMaintenanceArtifactRole roleForRecoveryPathFailure(
      SqliteCallerPathContractException exception,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    return SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            Objects.requireNonNull(exception, "exception").requestedPath(),
            Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath"))
        ? Objects.requireNonNull(secretArtifactRole, "secretArtifactRole")
        : Objects.requireNonNull(bookArtifactRole, "bookArtifactRole");
  }
}
