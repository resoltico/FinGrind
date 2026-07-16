package dev.erst.fingrind.contract.bookkeeping;

/** Maintenance-rejection narrative catalog split out from the public rejection facade. */
final class BookMaintenanceRejectionNarrative {
  private BookMaintenanceRejectionNarrative() {}

  static String message(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          "The selected book has blocking sibling artifacts and is not safe for closed-copy maintenance.";
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          "The selected backup source has blocking sibling artifacts and is not safe to restore from.";
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ ->
          "The selected backup source matches the selected live book; FinGrind will not restore a book from itself.";
      case BookMaintenanceRejection.ArtifactPathInvalid _ ->
          "The selected protected-book artifact path violates the filesystem contract for its declared role.";
      case BookMaintenanceRejection.ArtifactBusy _ ->
          "The selected protected-book artifact is actively in use and cannot be maintained safely.";
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          "The selected backup destination already exists and FinGrind will not overwrite it.";
      case BookMaintenanceRejection.SecretTargetOccupied _ ->
          "The selected generated-secret target already exists and FinGrind will not overwrite it.";
      case BookMaintenanceRejection.BookDestinationOccupied _ ->
          "The selected destination book already exists; pass --replace-existing-book to replace it.";
      case BookMaintenanceRejection.ArtifactVerificationFailed _ ->
          "The selected protected-book artifact failed verification for its declared role.";
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ ->
          "No sibling rekey rollback artifacts exist beside the selected book.";
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ ->
          "More than one sibling rekey rollback artifact exists; choose one explicit rollback artifact path.";
      case BookMaintenanceRejection.RollbackArtifactNotFound _ ->
          "The selected rollback artifact does not exist.";
      case BookMaintenanceRejection.RollbackArtifactNotForBook _ ->
          "The selected rollback artifact does not belong to the selected book.";
    };
  }
}
