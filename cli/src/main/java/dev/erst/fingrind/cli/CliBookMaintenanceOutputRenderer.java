package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Renders operator-facing text for attested protected-book maintenance workflows. */
final class CliBookMaintenanceOutputRenderer {
  private static final String BACKUP_BOOK_OPERATION = OperationId.BACKUP_BOOK.wireName();

  private CliBookMaintenanceOutputRenderer() {}

  static String renderBackupBookText(BackupBookResult.BackedUp backedUp) {
    return CliTextFormat.renderTitledBlock(
        "Book Backed Up",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(backedUp.bookFilePath())),
                List.of("Backup file", CliTextDisplay.path(backedUp.backupFilePath())),
                List.of("Backup key file", CliTextDisplay.path(backedUp.backupBookKeyFilePath())),
                List.of("Backup ID", backedUp.backupId().toString()),
                List.of(
                    "Acknowledgement",
                    backedUp.acknowledgementResumed() ? "resumed" : "acknowledged"))));
  }

  static String renderBackupAcknowledgementPendingText(
      BackupBookResult.AcknowledgementPending pending) {
    return CliTextFormat.renderTitledBlock(
        "Book Backup Published — Acknowledgement Pending",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Book file", CliTextDisplay.path(pending.bookFilePath())),
                List.of("Backup file", CliTextDisplay.path(pending.backupFilePath())),
                List.of("Backup key file", CliTextDisplay.path(pending.backupBookKeyFilePath())),
                List.of("Backup ID", pending.backupId().toString()),
                List.of(
                    "Next action",
                    "Rerun "
                        + BACKUP_BOOK_OPERATION
                        + " with these exact paths and --backup-id to resume acknowledgement."))));
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
