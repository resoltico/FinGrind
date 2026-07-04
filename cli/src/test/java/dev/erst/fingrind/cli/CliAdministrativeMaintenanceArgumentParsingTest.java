package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI maintenance command argument parsing. */
class CliAdministrativeMaintenanceArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsMaintenanceCommandsForValidArguments() {
    BackupBook backupBook =
        assertInstanceOf(
            BackupBook.class,
            CliArguments.parse(
                new String[] {
                  "backup-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--backup-file",
                  "backup/entity.sqlite",
                  "--backup-key-file",
                  "backup/entity.key",
                  "--output",
                  "text"
                }));
    RestoreBook restoreBook =
        assertInstanceOf(
            RestoreBook.class,
            CliArguments.parse(
                new String[] {
                  "restore-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--backup-file",
                  "backup/entity.sqlite",
                  "--backup-key-file",
                  "backup/entity.key"
                }));
    RestoreRekeyRollback restoreRekeyRollback =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--rollback-book-file",
                  "book.rekey-rollback.sqlite"
                }));
    DeleteRekeyRollback deleteRekeyRollback =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--rollback-book-file",
                  "book.rekey-rollback.sqlite"
                }));

    assertEquals(Path.of("book.sqlite"), backupBook.bookAccess().bookFilePath());
    assertEquals(Path.of("backup/entity.sqlite"), backupBook.backupFilePath());
    assertEquals(Path.of("backup/entity.key"), backupBook.backupBookKeyFilePath());
    assertEquals(OutputMode.TEXT, backupBook.outputMode());

    assertEquals(Path.of("book.sqlite"), restoreBook.bookFilePath());
    assertEquals(Path.of("book.key"), restoreBook.bookKeyFilePath());
    assertEquals(Path.of("backup/entity.sqlite"), restoreBook.backupFilePath());
    assertEquals(Path.of("backup/entity.key"), restoreBook.backupKeyFilePath());
    assertEquals(OutputMode.TEXT, restoreBook.outputMode());

    assertEquals(Path.of("book.sqlite"), restoreRekeyRollback.bookFilePath());
    assertEquals(
        Path.of("book.rekey-rollback.sqlite"), restoreRekeyRollback.rollbackArtifactPath());
    assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class, restoreRekeyRollback.expectedPassphraseSource());
    assertEquals(OutputMode.TEXT, restoreRekeyRollback.outputMode());

    assertEquals(Path.of("book.sqlite"), deleteRekeyRollback.bookAccess().bookFilePath());
    assertEquals(Path.of("book.rekey-rollback.sqlite"), deleteRekeyRollback.rollbackArtifactPath());
    assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class,
        deleteRekeyRollback.bookAccess().passphraseSource());
    assertEquals(OutputMode.TEXT, deleteRekeyRollback.outputMode());
  }

  @Test
  void parse_restoreRekeyRollback_acceptsStandardInputAndPromptPassphraseSources() {
    RestoreRekeyRollback standardInputRestore =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-stdin"
                }));
    RestoreRekeyRollback promptRestore =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-prompt"
                }));

    assertInstanceOf(
        BookAccess.PassphraseSource.StandardInput.class,
        standardInputRestore.expectedPassphraseSource());
    assertInstanceOf(
        BookAccess.PassphraseSource.InteractivePrompt.class,
        promptRestore.expectedPassphraseSource());
  }

  @Test
  void parse_restoreRekeyRollback_requiresOnePassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"restore-rekey-rollback", "--book-file", "book.sqlite"}));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Restore rekey rollback requires exactly one book passphrase source: --book-key-file <path>, --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_deleteRekeyRollback_acceptsStandardInputAndPromptPassphraseSources() {
    DeleteRekeyRollback standardInputDelete =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-stdin"
                }));
    DeleteRekeyRollback promptDelete =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-prompt"
                }));

    assertInstanceOf(
        BookAccess.PassphraseSource.StandardInput.class,
        standardInputDelete.bookAccess().passphraseSource());
    assertInstanceOf(
        BookAccess.PassphraseSource.InteractivePrompt.class,
        promptDelete.bookAccess().passphraseSource());
  }

  @Test
  void parse_deleteRekeyRollback_requiresOnePassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"delete-rekey-rollback", "--book-file", "book.sqlite"}));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Delete rekey rollback requires exactly one book passphrase source: --book-key-file <path>, --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_inspectRekeyRollback_rejectsPassphraseSourceArguments() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "inspect-rekey-rollback",
                      "--book-file",
                      "book.sqlite",
                      "--book-passphrase-stdin"
                    }));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Book passphrase source arguments are accepted only when delete-rekey-rollback or restore-rekey-rollback is selected.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsMaintenancePathCollisions() {
    CliArgumentsException backupCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "backup-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--backup-file",
                      "book.sqlite",
                      "--backup-key-file",
                      "backup.key"
                    }));
    CliArgumentsException restoreCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--backup-file",
                      "backup.sqlite",
                      "--backup-key-file",
                      "backup.sqlite"
                    }));

    assertEquals("--backup-file", backupCollision.argument());
    assertEquals(
        "--book-file and --backup-file must not point to the same path.",
        backupCollision.getMessage());
    assertEquals("--backup-key-file", restoreCollision.argument());
    assertEquals(
        "--backup-file and --backup-key-file must not point to the same path.",
        restoreCollision.getMessage());
  }
}
