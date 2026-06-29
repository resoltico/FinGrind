package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import java.util.List;

/** Renders operator-facing text for maintenance and rollback workflows. */
final class CliBookMaintenanceOutputRenderer {
  private CliBookMaintenanceOutputRenderer() {}

  static String renderBackupBookText(BackupBookResult.BackedUp backedUp) {
    return CliTextFormat.renderTitledBlock(
        "Book Backed Up",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(backedUp.bookFilePath())),
                List.of("Backup file", CliTextDisplay.path(backedUp.backupFilePath())),
                List.of(
                    "Backup key file", CliTextDisplay.path(backedUp.backupBookKeyFilePath())))));
  }

  static String renderRestoreBookText(RestoreBookResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(restored.bookFilePath())),
                List.of("Backup file", CliTextDisplay.path(restored.backupFilePath())),
                List.of("Book key file", CliTextDisplay.path(restored.backupBookKeyFilePath())))));
  }

  static String renderInspectRekeyRollbackText(RekeyRollbackResult.Inspected inspected) {
    String rollbackArtifacts =
        inspected.rollbackArtifactPaths().isEmpty()
            ? "(none)"
            : inspected.rollbackArtifactPaths().stream()
                .map(CliTextDisplay::path)
                .collect(java.util.stream.Collectors.joining(", "));
    return CliTextFormat.renderTitledBlock(
        "Rekey Rollback Artifacts",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(inspected.bookFilePath())),
                List.of("Rollback artifacts", rollbackArtifacts))));
  }

  static String renderRestoreRekeyRollbackText(RekeyRollbackResult.Restored restored) {
    return CliTextFormat.renderTitledBlock(
        "Book Restored From Rollback",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(restored.bookFilePath())),
                List.of(
                    "Rollback artifact", CliTextDisplay.path(restored.rollbackArtifactPath())))));
  }

  static String renderDeleteRekeyRollbackText(RekeyRollbackResult.Deleted deleted) {
    return CliTextFormat.renderTitledBlock(
        "Rollback Artifact Deleted",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(deleted.bookFilePath())),
                List.of(
                    "Rollback artifact", CliTextDisplay.path(deleted.rollbackArtifactPath())))));
  }
}
