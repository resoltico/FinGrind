package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transport contract for backup publication acknowledgement and restoration outcomes. */
class CliMaintenanceMutationResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  private static final Path BOOK_FILE = Path.of("books", "current.sqlite");
  private static final Path BACKUP_FILE = Path.of("backups", "current.sqlite");
  private static final Path BACKUP_KEY_FILE = Path.of("backups", "current.book-key");
  private static final UUID BACKUP_ID = UUID.fromString("c6547a5e-3404-4d7a-9cb9-2f0ae67e2f63");

  @Test
  void writesAcknowledgementPendingAcrossJsonTextAndUnsupportedCsv() {
    BackupBookResult.AcknowledgementPending pending =
        new BackupBookResult.AcknowledgementPending(
            BOOK_FILE, BACKUP_FILE, BACKUP_KEY_FILE, BACKUP_ID);

    ByteArrayOutputStream json = new ByteArrayOutputStream();
    writer(json).writeBackupBookResult(pending, OutputMode.JSON);
    assertJsonContains(json, "\"acknowledgementState\":\"pending\"");
    assertJsonContains(json, "\"backupId\":\"" + BACKUP_ID + "\"");

    ByteArrayOutputStream text = new ByteArrayOutputStream();
    writer(text).writeBackupBookResult(pending, OutputMode.TEXT);
    String rendered = text.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Book Backup Published"), rendered);
    assertTrue(rendered.contains("--backup-id"), rendered);
    assertTrue(rendered.contains(BACKUP_ID.toString()), rendered);

    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeBackupBookResult(pending, OutputMode.CSV));
    assertEquals(4, CliAdministrativeExitCodes.exitCodeFor(pending));
  }

  @Test
  void writesResumedBackupRestorationAndAcknowledgementConflictWithoutFlatteningTheirMeaning() {
    BackupBookResult.BackedUp resumed =
        new BackupBookResult.BackedUp(BOOK_FILE, BACKUP_FILE, BACKUP_KEY_FILE, BACKUP_ID, true);
    ByteArrayOutputStream resumedText = new ByteArrayOutputStream();
    writer(resumedText).writeBackupBookResult(resumed, OutputMode.TEXT);
    assertTrue(resumedText.toString(StandardCharsets.UTF_8).contains("resumed"));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeBackupBookResult(resumed, OutputMode.CSV));

    BackupBookResult.BackedUp acknowledged =
        new BackupBookResult.BackedUp(BOOK_FILE, BACKUP_FILE, BACKUP_KEY_FILE, BACKUP_ID, false);
    ByteArrayOutputStream acknowledgedJson = new ByteArrayOutputStream();
    writer(acknowledgedJson).writeBackupBookResult(acknowledged, OutputMode.JSON);
    assertJsonContains(acknowledgedJson, "\"acknowledgementState\":\"acknowledged\"");

    RestoreBookResult.Restored restored =
        new RestoreBookResult.Restored(BOOK_FILE, Path.of("keys", "restored.key"));
    ByteArrayOutputStream restoredText = new ByteArrayOutputStream();
    writer(restoredText).writeRestoreBookResult(restored, OutputMode.TEXT);
    assertTrue(restoredText.toString(StandardCharsets.UTF_8).contains("Book Restored"));
    assertThrows(
        IllegalArgumentException.class,
        () -> writer(new ByteArrayOutputStream()).writeRestoreBookResult(restored, OutputMode.CSV));
    assertEquals(0, CliAdministrativeExitCodes.exitCodeFor(restored));

    BackupBookResult.Rejected conflict =
        new BackupBookResult.Rejected(
            new BookMaintenanceRejection.BackupAcknowledgementConflict(BACKUP_ID));
    ByteArrayOutputStream rejected = new ByteArrayOutputStream();
    writer(rejected).writeBackupBookResult(conflict, OutputMode.JSON);
    assertJsonContains(rejected, "\"code\":\"backup-acknowledgement-conflict\"");
    assertJsonContains(rejected, "\"backupId\":\"" + BACKUP_ID + "\"");
    assertEquals(7, CliAdministrativeExitCodes.exitCodeFor(conflict));
  }

  private static CliMaintenanceMutationResponseWriter writer(ByteArrayOutputStream outputStream) {
    return new CliMaintenanceMutationResponseWriter(outputChannel(outputStream));
  }
}
