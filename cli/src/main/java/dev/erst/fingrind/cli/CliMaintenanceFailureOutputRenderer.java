package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import java.util.List;

/** Renders maintenance-specific rejection detail rows for deterministic CLI text output. */
final class CliMaintenanceFailureOutputRenderer {
  private CliMaintenanceFailureOutputRenderer() {}

  static void appendRows(
      List<List<String>> rows,
      CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliMaintenanceRejectionJsonModels.BookFileDetails details ->
          rows.add(List.of("Book file", redactedPath(details.bookFile())));
      case CliMaintenanceRejectionJsonModels.BookAndBackupFileDetails details -> {
        rows.add(List.of("Book file", redactedPath(details.bookFile())));
        rows.add(List.of("Backup file", redactedPath(details.backupFile())));
      }
      case CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails details -> {
        rows.add(List.of("Book target", redactedPath(details.bookTarget())));
        rows.add(List.of("Generated secret target", redactedPath(details.generatedSecretTarget())));
      }
      case CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails details -> {
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
      case CliMaintenanceRejectionJsonModels.ArtifactBusyDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", redactedPath(details.artifactPath())));
      }
      case CliMaintenanceRejectionJsonModels.BackupAcknowledgementConflictDetails details ->
          rows.add(List.of("Backup ID", details.backupId()));
      case CliMaintenanceRejectionJsonModels.BackupFileDetails details ->
          rows.add(List.of("Backup file", redactedPath(details.backupFile())));
      case CliMaintenanceRejectionJsonModels.SecretTargetDetails details ->
          rows.add(List.of("Secret target", redactedPath(details.secretTarget())));
      case CliMaintenanceRejectionJsonModels.RecoveryPendingDetails details -> {
        rows.add(List.of("Recovery operation", details.recoveryOperation()));
        rows.add(List.of("Book target", redactedPath(details.bookTarget())));
        rows.add(List.of("Generated secret target", redactedPath(details.generatedSecretTarget())));
      }
      case CliMaintenanceRejectionJsonModels.ArtifactVerificationFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", redactedPath(details.artifactPath())));
        rows.add(List.of("Verification failure", details.verificationFailure()));
      }
    }
  }

  private static String redactedPath(String absolutePath) {
    return CliTextDisplay.serializedAbsolutePath(absolutePath);
  }
}
