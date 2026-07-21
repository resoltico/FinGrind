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
          rows.add(List.of("Book file", redactedPath(details.bookFile())));
      case CliRejectionJsonModels.BookAndBackupFileDetails details -> {
        rows.add(List.of("Book file", redactedPath(details.bookFile())));
        rows.add(List.of("Backup file", redactedPath(details.backupFile())));
      }
      case CliRejectionJsonModels.BlockingArtifactsDetails details -> {
        rows.add(List.of("Book file", redactedPath(details.bookFile())));
        rows.add(
            List.of(
                "Blocking artifacts",
                CliTextFormat.joined(
                    details.blockingArtifacts().stream()
                        .map(CliMaintenanceFailureOutputRenderer::redactedPath)
                        .toList())));
      }
      case CliArtifactPathFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", redactedPath(details.artifactPath())));
        rows.add(List.of("Path failure", details.pathFailure()));
      }
      case CliRejectionJsonModels.ArtifactBusyDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", redactedPath(details.artifactPath())));
      }
      case CliRejectionJsonModels.BackupAcknowledgementConflictDetails details ->
          rows.add(List.of("Backup ID", details.backupId()));
      case CliRejectionJsonModels.BackupFileDetails details ->
          rows.add(List.of("Backup file", redactedPath(details.backupFile())));
      case CliRejectionJsonModels.SecretTargetDetails details ->
          rows.add(List.of("Secret target", redactedPath(details.secretTarget())));
      case CliRejectionJsonModels.ArtifactVerificationFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", redactedPath(details.artifactPath())));
        rows.add(List.of("Verification failure", details.verificationFailure()));
      }
    }
  }

  private static String redactedPath(String absolutePath) {
    return CliTextDisplay.path(java.nio.file.Path.of(absolutePath));
  }
}
