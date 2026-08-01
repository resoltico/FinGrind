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
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(backedUp.bookFilePath())));
    rows.add(List.of("Backup file", CliTextDisplay.path(backedUp.backupFilePath())));
    rows.add(List.of("Backup key file", CliTextDisplay.path(backedUp.backupBookKeyFilePath())));
    rows.add(List.of("Backup ID", backedUp.backupId().toString()));
    rows.add(
        List.of("Pair publication completion", backedUp.pairPublicationCompletion().wireValue()));
    CliProtectedBookPairPublicationRetentionPresentation.appendTextRows(
        rows, backedUp.pairPublicationRetention());
    rows.add(List.of("Acknowledgement", backedUp.acknowledgementState().wireValue()));
    CliAttestationCommitPresentation.appendTextRows(
        rows, backedUp.attestationCommit(), "No operation appended (acknowledgement replay)");
    return CliTextFormat.renderTitledBlock(
        "Book Backed Up", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderBackupAcknowledgementPendingText(
      BackupBookResult.AcknowledgementPending pending) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(pending.bookFilePath())));
    rows.add(List.of("Backup file", CliTextDisplay.path(pending.backupFilePath())));
    rows.add(List.of("Backup key file", CliTextDisplay.path(pending.backupBookKeyFilePath())));
    rows.add(List.of("Backup ID", pending.backupId().toString()));
    rows.add(
        List.of("Pair publication completion", pending.pairPublicationCompletion().wireValue()));
    CliProtectedBookPairPublicationRetentionPresentation.appendTextRows(
        rows, pending.pairPublicationRetention());
    rows.add(
        List.of(
            "Next action",
            "Rerun "
                + BACKUP_BOOK_OPERATION
                + " with these exact paths and --backup-id to resume acknowledgement."));
    return CliTextFormat.renderTitledBlock(
        "Book Backup Published — Acknowledgement Pending",
        CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRestoreBookText(RestoreBookResult.Restored restored) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(restored.bookFilePath())));
    rows.add(List.of("Book key file", CliTextDisplay.path(restored.bookKeyFilePath())));
    rows.add(
        List.of("Pair publication completion", restored.pairPublicationCompletion().wireValue()));
    CliProtectedBookPairPublicationRetentionPresentation.appendTextRows(
        rows, restored.pairPublicationRetention());
    CliAttestationCommitPresentation.appendTextRows(
        rows, restored.attestationCommit(), "No attestation operation was returned");
    return CliTextFormat.renderTitledBlock(
        "Book Restored", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }
}
