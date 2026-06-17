package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import java.util.List;

/** Renders maintenance-specific rejection detail rows for deterministic CLI text output. */
final class CliMaintenanceFailureOutputRenderer {
  private CliMaintenanceFailureOutputRenderer() {}

  static void appendRows(
      List<List<String>> rows,
      CliRejectionJsonModels.MaintenanceRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.BookFileDetails details ->
          rows.add(List.of("Book file", details.bookFile()));
      case CliRejectionJsonModels.BookAndBackupFileDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Backup file", details.backupFile()));
      }
      case CliRejectionJsonModels.BlockingArtifactsDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Blocking artifacts", CliTextFormat.joined(details.blockingArtifacts())));
      }
      case CliArtifactPathFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", details.artifactPath()));
        rows.add(List.of("Path failure", details.pathFailure()));
      }
      case CliRejectionJsonModels.ArtifactBusyDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", details.artifactPath()));
      }
      case CliRejectionJsonModels.BackupFileDetails details ->
          rows.add(List.of("Backup file", details.backupFile()));
      case CliRejectionJsonModels.BackupBookKeyFileDetails details ->
          rows.add(List.of("Backup key file", details.backupBookKeyFile()));
      case CliRejectionJsonModels.ArtifactVerificationFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", details.artifactPath()));
        rows.add(List.of("Verification failure", details.verificationFailure()));
      }
      case CliRejectionJsonModels.RollbackArtifactDetails details ->
          rows.add(List.of("Rollback artifact", details.rollbackArtifact()));
      case CliRejectionJsonModels.RollbackArtifactMismatchDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Rollback artifact", details.rollbackArtifact()));
      }
      case CliRejectionJsonModels.RollbackArtifactSelectionDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Rollback artifacts", CliTextFormat.joined(details.rollbackArtifacts())));
      }
    }
  }
}
