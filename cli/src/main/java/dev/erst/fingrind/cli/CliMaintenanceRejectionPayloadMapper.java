package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;

/** Maps maintenance-command rejections into CLI rejected envelopes. */
final class CliMaintenanceRejectionPayloadMapper {
  private static final String BACKUP_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.BACKUP_BOOK);
  private static final String INSPECT_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_BOOK);
  private static final String RESTORE_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.RESTORE_BOOK);

  private CliMaintenanceRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope(BookMaintenanceRejection rejection) {
    return CliEnvelopeMapper.withFailurePaths(
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            BookMaintenanceRejection.wireCode(rejection),
            RejectionNarrative.message(rejection),
            rejectionHint(rejection),
            null,
            null,
            rejectionDetails(rejection),
            null,
            null,
            null,
            null));
  }

  private static String rejectionHint(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          "Close every process using the selected book, resolve its SQLite sidecars through the current lifecycle workflow, and rerun the maintenance command after "
              + INSPECT_BOOK_OPERATION
              + " confirms a clean closed-copy state.";
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          "Choose an encrypted backup copy with no sibling SQLite sidecars or rollback artifacts, or recreate the backup with "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ ->
          "Choose a backup copy path that differs from the selected --book-file path, then rerun "
              + RESTORE_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.PairTargetsConflict _ ->
          "Choose a generated-secret target with a distinct filesystem identity from the selected protected-book target, then rerun the maintenance command.";
      case BookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          CliMaintenancePathFailureHint.forFailure(invalidArtifactPath.pathFailure());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          "Close the process using the "
              + artifactBusy.artifactRole().wireValue()
              + " artifact, wait for the active maintenance workflow to finish, then rerun the command.";
      case BookMaintenanceRejection.BackupAcknowledgementConflict conflict ->
          "Use a new --backup-id, or resume the exact backup whose immutable acknowledgement uses "
              + conflict.backupId()
              + ".";
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          "Choose a new --backup-file path or remove the existing encrypted backup copy yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.SecretTargetOccupied _ ->
          "Choose one absent generated-secret target path, then rerun the maintenance command.";
      case BookMaintenanceRejection.BookDestinationOccupied _ ->
          "Choose an absent destination book path, then rerun " + RESTORE_BOOK_OPERATION + ".";
      case BookMaintenanceRejection.RecoveryPending recoveryPending ->
          "Resume "
              + ProtocolCatalog.operationName(recoveryPending.recoveryOperation())
              + " with its complete original inputs, including exactly the retained book and generated-secret target paths shown below. Preserve its recovery evidence; do not rename, overwrite, delete, recreate, or manually clean it.";
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          "Use an artifact that opens as an initialized FinGrind protected book for role "
              + verificationFailed.artifactRole().wireValue()
              + ", with the matching passphrase source for that artifact, then rerun the maintenance command.";
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          new CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails(
              CliPublicPaths.absoluteValue(blockingArtifacts.bookFilePath()),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(CliPublicPaths::absoluteValue)
                  .toList());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails(
              CliPublicPaths.absoluteValue(blockingArtifacts.backupFilePath()),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(CliPublicPaths::absoluteValue)
                  .toList());
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          new CliMaintenanceRejectionJsonModels.BookAndBackupFileDetails(
              CliPublicPaths.absoluteValue(sourceMatchesLiveBook.bookFilePath()),
              CliPublicPaths.absoluteValue(sourceMatchesLiveBook.backupFilePath()));
      case BookMaintenanceRejection.PairTargetsConflict conflict ->
          new CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails(
              CliPublicPaths.absoluteValue(conflict.bookTarget()),
              CliPublicPaths.absoluteValue(conflict.generatedSecretTarget()));
      case BookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          new CliArtifactPathFailureDetails(
              invalidArtifactPath.artifactRole().wireValue(),
              CliPublicPaths.absoluteValue(invalidArtifactPath.artifactPath()),
              invalidArtifactPath.pathFailure().wireValue());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          new CliMaintenanceRejectionJsonModels.ArtifactBusyDetails(
              artifactBusy.artifactRole().wireValue(),
              CliPublicPaths.absoluteValue(artifactBusy.artifactPath()));
      case BookMaintenanceRejection.BackupAcknowledgementConflict conflict ->
          new CliMaintenanceRejectionJsonModels.BackupAcknowledgementConflictDetails(
              conflict.backupId().toString());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          new CliMaintenanceRejectionJsonModels.BackupFileDetails(
              CliPublicPaths.absoluteValue(destinationAlreadyExists.backupFilePath()));
      case BookMaintenanceRejection.SecretTargetOccupied targetOccupied ->
          new CliMaintenanceRejectionJsonModels.SecretTargetDetails(
              CliPublicPaths.absoluteValue(targetOccupied.secretTargetPath()));
      case BookMaintenanceRejection.BookDestinationOccupied destinationOccupied ->
          new CliMaintenanceRejectionJsonModels.BookFileDetails(
              CliPublicPaths.absoluteValue(destinationOccupied.bookFilePath()));
      case BookMaintenanceRejection.RecoveryPending recoveryPending ->
          new CliMaintenanceRejectionJsonModels.RecoveryPendingDetails(
              recoveryPending.recoveryOperation().wireValue(),
              CliPublicPaths.absoluteValue(recoveryPending.bookTargetPath()),
              CliPublicPaths.absoluteValue(recoveryPending.generatedSecretTargetPath()));
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          new CliMaintenanceRejectionJsonModels.ArtifactVerificationFailureDetails(
              verificationFailed.artifactRole().wireValue(),
              CliPublicPaths.absoluteValue(verificationFailed.artifactPath()),
              verificationFailed.verificationFailure().wireValue());
    };
  }
}
