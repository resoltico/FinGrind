package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import java.util.List;

/** Renders operator-facing text for attested protected-book maintenance workflows. */
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
                List.of("Book key file", CliTextDisplay.path(restored.bookKeyFilePath())))));
  }
}
