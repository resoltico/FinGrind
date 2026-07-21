package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for complete, duplicate, and structurally incomplete backup/restore commands.
 */
class CliBackupRestoreArgumentParsingTest {
  private static final String PRINCIPAL_ID = "9b739e87-01fc-4b08-a68d-90e712df9c4a";

  @Test
  void parseBackupAndRestore_acceptCompleteIndependentArtifactArguments() {
    BackupBook backup =
        assertInstanceOf(
            BackupBook.class,
            CliBackupRestoreArguments.parseBackupBookCommand(backupArguments(OutputMode.JSON)));
    assertEquals(OutputMode.JSON, backup.outputMode());
    assertEquals("books/current.sqlite", backup.bookAccess().bookFilePath().toString());
    assertEquals("backups/current.sqlite", backup.backupFilePath().toString());

    RestoreBook restore =
        assertInstanceOf(
            RestoreBook.class,
            CliBackupRestoreArguments.parseRestoreBookCommand(restoreArguments(OutputMode.TEXT)));
    assertEquals(OutputMode.TEXT, restore.outputMode());
    assertEquals(1, restore.attestationCredentialSources().size());
    assertEquals("books/restored.sqlite", restore.bookFilePath().toString());
  }

  @Test
  void parseBackup_rejectsMissingAndDuplicateRequiredFields() {
    assertArgument(
        "--backup-file",
        () ->
            CliBackupRestoreArguments.parseBackupBookCommand(
                List.of(
                    "backup-book",
                    "--book-file",
                    "books/current.sqlite",
                    "--book-key-file",
                    "keys/current.key")));
    assertArgument(
        "--new-backup-key-file",
        () ->
            CliBackupRestoreArguments.parseBackupBookCommand(
                List.of(
                    "backup-book",
                    "--book-file",
                    "books/current.sqlite",
                    "--book-key-file",
                    "keys/current.key",
                    "--backup-file",
                    "backups/current.sqlite")));
    assertArgument(
        "--backup-id",
        () ->
            CliBackupRestoreArguments.parseBackupBookCommand(
                List.of(
                    "backup-book",
                    "--book-file",
                    "books/current.sqlite",
                    "--book-key-file",
                    "keys/current.key",
                    "--backup-file",
                    "backups/current.sqlite",
                    "--new-backup-key-file",
                    "backups/current.key")));

    List<String> duplicateBackupFile = new ArrayList<>(backupArguments(OutputMode.TEXT));
    duplicateBackupFile.addAll(List.of("--backup-file", "backups/duplicate.sqlite"));
    assertArgument(
        "--backup-file",
        () -> CliBackupRestoreArguments.parseBackupBookCommand(duplicateBackupFile));

    List<String> duplicateBackupId = new ArrayList<>(backupArguments(OutputMode.TEXT));
    duplicateBackupId.addAll(List.of("--backup-id", "5de5f1ea-23ec-4b12-8f2d-e50a53a339f4"));
    assertArgument(
        "--backup-id", () -> CliBackupRestoreArguments.parseBackupBookCommand(duplicateBackupId));
  }

  @Test
  void parseRestore_rejectsEachMissingArtifactAndUnalignedCredentialTriple() {
    assertArgument(
        "--book-file",
        () ->
            CliBackupRestoreArguments.parseRestoreBookCommand(
                restoreArgumentsWithout("--book-file")));
    assertArgument(
        "--new-book-key-file",
        () ->
            CliBackupRestoreArguments.parseRestoreBookCommand(
                restoreArgumentsWithout("--new-book-key-file")));
    assertArgument(
        "--backup-file",
        () ->
            CliBackupRestoreArguments.parseRestoreBookCommand(
                restoreArgumentsWithout("--backup-file")));
    assertArgument(
        "--backup-key-file",
        () ->
            CliBackupRestoreArguments.parseRestoreBookCommand(
                restoreArgumentsWithout("--backup-key-file")));

    List<String> noCredentials = restoreArguments(OutputMode.JSON);
    noCredentials.subList(9, 15).clear();
    assertArgument(
        "--attestation-principal-id",
        () -> CliBackupRestoreArguments.parseRestoreBookCommand(noCredentials));

    List<String> duplicateBookFile = new ArrayList<>(restoreArguments(OutputMode.JSON));
    duplicateBookFile.addAll(List.of("--book-file", "books/other.sqlite"));
    assertArgument(
        "--book-file", () -> CliBackupRestoreArguments.parseRestoreBookCommand(duplicateBookFile));
  }

  private static List<String> backupArguments(OutputMode outputMode) {
    return List.of(
        "backup-book",
        "--book-file",
        "books/current.sqlite",
        "--book-key-file",
        "keys/current.key",
        "--backup-file",
        "backups/current.sqlite",
        "--new-backup-key-file",
        "backups/current.key",
        "--backup-id",
        "4ef958b9-13ae-40f3-a2b1-9714406b279f",
        "--output",
        outputMode.wireValue());
  }

  private static List<String> restoreArguments(OutputMode outputMode) {
    return new ArrayList<>(
        List.of(
            "restore-book",
            "--book-file",
            "books/restored.sqlite",
            "--new-book-key-file",
            "keys/restored.key",
            "--backup-file",
            "backups/current.sqlite",
            "--backup-key-file",
            "backups/current.key",
            "--attestation-principal-id",
            PRINCIPAL_ID,
            "--attestation-key-file",
            "keys/founder.fgatk",
            "--attestation-passphrase-file",
            "keys/founder.passphrase",
            "--output",
            outputMode.wireValue()));
  }

  private static List<String> restoreArgumentsWithout(String option) {
    List<String> values = restoreArguments(OutputMode.JSON);
    int index = values.indexOf(option);
    values.subList(index, index + 2).clear();
    return values;
  }

  private static void assertArgument(
      String expectedArgument, org.junit.jupiter.api.function.Executable action) {
    CliArgumentsException exception = assertThrows(CliArgumentsException.class, action);
    assertEquals(expectedArgument, exception.argument());
  }
}
