package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
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
  private static final String DELETE_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.DELETE_REKEY_ROLLBACK);
  private static final String INSPECT_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_BOOK);
  private static final String INSPECT_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_REKEY_ROLLBACK);
  private static final String RESTORE_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.RESTORE_BOOK);
  private static final String RESTORE_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.RESTORE_REKEY_ROLLBACK);

  private CliMaintenanceRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope(BookMaintenanceRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        BookMaintenanceRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        rejectionHint(rejection),
        null,
        null,
        rejectionDetails(rejection),
        null);
  }

  private static String rejectionHint(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          "Close every process using the selected book, remove SQLite sidecars only by finishing or restoring the interrupted workflow, and rerun the maintenance command after "
              + INSPECT_BOOK_OPERATION
              + " and "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " confirm a clean closed-copy state.";
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          "Choose an encrypted backup copy with no sibling SQLite sidecars or rollback artifacts, or recreate the backup with "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ ->
          "Choose a backup copy path that differs from the selected --book-file path, then rerun "
              + RESTORE_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          pathFailureHint(invalidArtifactPath);
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          "Close the process using the "
              + artifactBusy.artifactRole().wireValue()
              + " artifact, wait for the active maintenance workflow to finish, then rerun the command.";
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          "Choose a new --backup-file path or remove the existing encrypted backup copy yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists _ ->
          "Choose a new --backup-key-file path or remove the existing key file yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          "Use an artifact that opens as an initialized FinGrind protected book for role "
              + verificationFailed.artifactRole().wireValue()
              + ", with the matching passphrase source for that artifact, then rerun the maintenance command.";
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ ->
          "Rerun "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " to confirm that no stale rollback copies remain.";
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ ->
          "Rerun "
              + RESTORE_REKEY_ROLLBACK_OPERATION
              + " or "
              + DELETE_REKEY_ROLLBACK_OPERATION
              + " with an explicit --rollback-book-file path from details.rollbackArtifacts.";
      case BookMaintenanceRejection.RollbackArtifactNotFound _ ->
          "Choose an existing rollback artifact path returned by "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " inspection output and rerun the command.";
      case BookMaintenanceRejection.RollbackArtifactNotForBook _ ->
          "Choose a rollback artifact that lives beside the selected --book-file and matches FinGrind's canonical rollback naming.";
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          new CliRejectionJsonModels.BlockingArtifactsDetails(
              blockingArtifacts.bookFilePath().value(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new CliRejectionJsonModels.BlockingArtifactsDetails(
              blockingArtifacts.backupFilePath().value(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          new CliRejectionJsonModels.BookAndBackupFileDetails(
              sourceMatchesLiveBook.bookFilePath().value(),
              sourceMatchesLiveBook.backupFilePath().value());
      case BookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          new CliArtifactPathFailureDetails(
              invalidArtifactPath.artifactRole().wireValue(),
              invalidArtifactPath.artifactPath().value(),
              invalidArtifactPath.pathFailure().wireValue());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          new CliRejectionJsonModels.ArtifactBusyDetails(
              artifactBusy.artifactRole().wireValue(), artifactBusy.artifactPath().value());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupFileDetails(
              destinationAlreadyExists.backupFilePath().value());
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupBookKeyFileDetails(
              destinationAlreadyExists.backupBookKeyFilePath().value());
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          new CliRejectionJsonModels.ArtifactVerificationFailureDetails(
              verificationFailed.artifactRole().wireValue(),
              verificationFailed.artifactPath().value(),
              verificationFailed.verificationFailure().wireValue());
      case BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          new CliRejectionJsonModels.BookFileDetails(
              noRollbackArtifactsFound.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          new CliRejectionJsonModels.RollbackArtifactSelectionDetails(
              selectionRequired.bookFilePath().value(),
              selectionRequired.rollbackArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.RollbackArtifactNotFound rollbackArtifactNotFound ->
          new CliRejectionJsonModels.RollbackArtifactDetails(
              rollbackArtifactNotFound.rollbackArtifactPath().value());
      case BookMaintenanceRejection.RollbackArtifactNotForBook rollbackArtifactNotForBook ->
          new CliRejectionJsonModels.RollbackArtifactMismatchDetails(
              rollbackArtifactNotForBook.bookFilePath().value(),
              rollbackArtifactNotForBook.rollbackArtifactPath().value());
    };
  }

  private static String pathFailureHint(BookMaintenanceRejection.ArtifactPathInvalid rejection) {
    return switch (rejection.pathFailure()) {
      case MISSING_PARENT_DIRECTORY ->
          "Choose a path whose parent directory already exists or whose missing parent chain FinGrind can create securely, then rerun the maintenance command.";
      case PARENT_PATH_COLLISION ->
          "Choose a path whose parent chain is made only of real directories, not existing files or symlinks, then rerun the maintenance command.";
      case PARENT_OWNER_ACCESS_REQUIRED ->
          "Choose a path beneath a parent directory that the owner can traverse and write, then rerun the maintenance command.";
      case PARENT_OWNER_ONLY_REQUIRED ->
          "Choose a path beneath an owner-only parent directory, or tighten the existing parent directory first, then rerun the maintenance command.";
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE ->
          "Choose a regular non-symlink artifact path for this maintenance workflow, then rerun the command.";
      case UNSUPPORTED_SECURE_FILESYSTEM ->
          "Choose a path on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs, then rerun the maintenance command.";
    };
  }
}
