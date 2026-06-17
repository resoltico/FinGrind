package dev.erst.fingrind.contract.bookkeeping;

/** Maintenance-rejection narrative catalog split out from the public rejection facade. */
final class BookMaintenanceRejectionNarrative {
  private BookMaintenanceRejectionNarrative() {}

  static String message(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          "Book '%s' has blocking sibling artifacts and is not safe for one closed-copy maintenance workflow."
              .formatted(blockingArtifacts.bookFilePath().value());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          "Backup source '%s' has blocking sibling artifacts and is not safe to restore from."
              .formatted(blockingArtifacts.backupFilePath().value());
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          "Backup source '%s' matches live book '%s'; FinGrind will not restore a book from itself."
              .formatted(
                  sourceMatchesLiveBook.backupFilePath().value(),
                  sourceMatchesLiveBook.bookFilePath().value());
      case BookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          "Protected-book artifact path '%s' for role '%s' violates the filesystem contract: '%s'."
              .formatted(
                  invalidArtifactPath.artifactPath().value(),
                  invalidArtifactPath.artifactRole().wireValue(),
                  invalidArtifactPath.pathFailure().wireValue());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          "Protected-book artifact '%s' with role '%s' is actively in use and cannot be maintained safely."
              .formatted(
                  artifactBusy.artifactPath().value(), artifactBusy.artifactRole().wireValue());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          "Backup destination '%s' already exists and FinGrind will not overwrite it."
              .formatted(destinationAlreadyExists.backupFilePath().value());
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists destinationAlreadyExists ->
          "Backup key file '%s' already exists and FinGrind will not overwrite it."
              .formatted(destinationAlreadyExists.backupBookKeyFilePath().value());
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          "Protected-book artifact '%s' with role '%s' failed verification: '%s'."
              .formatted(
                  verificationFailed.artifactPath().value(),
                  verificationFailed.artifactRole().wireValue(),
                  verificationFailed.verificationFailure().wireValue());
      case BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          "No sibling rekey rollback artifacts exist beside '%s'."
              .formatted(noRollbackArtifactsFound.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          "More than one sibling rekey rollback artifact exists beside '%s'; choose one explicit rollback artifact path."
              .formatted(selectionRequired.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactNotFound rollbackArtifactNotFound ->
          "Rollback artifact '%s' does not exist."
              .formatted(rollbackArtifactNotFound.rollbackArtifactPath().value());
      case BookMaintenanceRejection.RollbackArtifactNotForBook rollbackArtifactNotForBook ->
          "Rollback artifact '%s' does not belong to book '%s'."
              .formatted(
                  rollbackArtifactNotForBook.rollbackArtifactPath().value(),
                  rollbackArtifactNotForBook.bookFilePath().value());
    };
  }
}
