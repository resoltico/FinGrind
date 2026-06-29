package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers maintenance-command CLI behavior that spans parsing, rendering, and workflow seams. */
class CliMaintenanceCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void maintenanceArgumentParsing_coversDefaultsAndValidationBranches() {
    InspectRekeyRollback defaultInspect =
        assertInstanceOf(
            InspectRekeyRollback.class,
            CliArguments.parse(
                new String[] {"inspect-rekey-rollback", "--book-file", "book.sqlite"}));
    assertEquals(Path.of("book.sqlite"), defaultInspect.bookFilePath());
    assertEquals(OutputMode.TEXT, defaultInspect.outputMode());

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
                  "--backup-book-file-out",
                  "backup/entity.sqlite",
                  "--backup-book-key-file-out",
                  "backup/entity.key"
                }));
    assertEquals(Path.of("backup/entity.sqlite"), backupBook.backupFilePath());

    BackupBook backupBookWithTextOutput =
        assertInstanceOf(
            BackupBook.class,
            CliArguments.parse(
                new String[] {
                  "backup-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--backup-book-file-out",
                  "backup/entity.sqlite",
                  "--backup-book-key-file-out",
                  "backup/entity.key",
                  "--output",
                  "text"
                }));
    assertEquals(OutputMode.TEXT, backupBookWithTextOutput.outputMode());

    RestoreBook restoreBook =
        assertInstanceOf(
            RestoreBook.class,
            CliArguments.parse(
                new String[] {
                  "restore-book",
                  "--book-file",
                  "book.sqlite",
                  "--backup-book-file",
                  "backup/entity.sqlite",
                  "--backup-book-key-file",
                  "backup/entity.key",
                  "--output",
                  "text"
                }));
    assertEquals(OutputMode.TEXT, restoreBook.outputMode());

    CliArgumentsException missingBackupFile =
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
                      "--backup-book-key-file-out",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-book-file-out", missingBackupFile.argument());
    assertEquals("A --backup-book-file-out argument is required.", missingBackupFile.getMessage());

    CliArgumentsException missingBackupKeyFile =
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
                      "--backup-book-file-out",
                      "backup/entity.sqlite"
                    }));
    assertEquals("--backup-book-key-file-out", missingBackupKeyFile.argument());

    CliArgumentsException duplicateBackupFile =
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
                      "--backup-book-file-out",
                      "backup/entity.sqlite",
                      "--backup-book-file-out",
                      "backup/entity-2.sqlite",
                      "--backup-book-key-file-out",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-book-file-out", duplicateBackupFile.argument());

    CliArgumentsException duplicateBackupKeyFile =
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
                      "--backup-book-file-out",
                      "backup/entity.sqlite",
                      "--backup-book-key-file-out",
                      "backup/entity.key",
                      "--backup-book-key-file-out",
                      "backup/entity-2.key"
                    }));
    assertEquals("--backup-book-key-file-out", duplicateBackupKeyFile.argument());

    CliArgumentsException unsupportedBackupArgument =
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
                      "--backup-book-file-out",
                      "backup/entity.sqlite",
                      "--backup-book-key-file-out",
                      "backup/entity.key",
                      "--explode"
                    }));
    assertEquals("--explode", unsupportedBackupArgument.argument());

    CliArgumentsException restoreMissingBookFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--backup-book-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--book-file", restoreMissingBookFile.argument());
    assertEquals("A --book-file argument is required.", restoreMissingBookFile.getMessage());

    CliArgumentsException restoreMissingBackupFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-book-file", restoreMissingBackupFile.argument());

    CliArgumentsException restoreMissingBackupKeyFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-book-file",
                      "backup/entity.sqlite"
                    }));
    assertEquals("--backup-book-key-file", restoreMissingBackupKeyFile.argument());

    CliArgumentsException duplicateRestoreBookFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-file",
                      "book-2.sqlite",
                      "--backup-book-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--book-file", duplicateRestoreBookFile.argument());

    CliArgumentsException duplicateRestoreBackupFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-book-file",
                      "backup/entity.sqlite",
                      "--backup-book-file",
                      "backup/entity-2.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-book-file", duplicateRestoreBackupFile.argument());

    CliArgumentsException duplicateRestoreBackupKeyFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-book-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key",
                      "--backup-book-key-file",
                      "backup/entity-2.key"
                    }));
    assertEquals("--backup-book-key-file", duplicateRestoreBackupKeyFile.argument());

    CliArgumentsException unsupportedRestoreArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-book-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key",
                      "--explode"
                    }));
    assertEquals("--explode", unsupportedRestoreArgument.argument());

    CliArgumentsException duplicateRollbackFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "delete-rekey-rollback",
                      "--book-file",
                      "book.sqlite",
                      "--rollback-book-file",
                      "rollback-a.sqlite",
                      "--rollback-book-file",
                      "rollback-b.sqlite"
                    }));
    assertEquals("--rollback-book-file", duplicateRollbackFile.argument());

    CliArgumentsException inspectRollbackPathRejected =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "inspect-rekey-rollback",
                      "--book-file",
                      "book.sqlite",
                      "--rollback-book-file",
                      "rollback.sqlite"
                    }));
    assertEquals("--rollback-book-file", inspectRollbackPathRejected.argument());
    assertEquals(
        "--rollback-book-file is accepted only when delete-rekey-rollback or restore-rekey-rollback is selected.",
        inspectRollbackPathRejected.getMessage());

    InspectRekeyRollback inspectWithTextOutput =
        assertInstanceOf(
            InspectRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "inspect-rekey-rollback", "--book-file", "book.sqlite", "--output", "text"
                }));
    assertEquals(OutputMode.TEXT, inspectWithTextOutput.outputMode());

    CliArgumentsException duplicateInspectBookFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "inspect-rekey-rollback",
                      "--book-file",
                      "book.sqlite",
                      "--book-file",
                      "book-2.sqlite"
                    }));
    assertEquals("--book-file", duplicateInspectBookFile.argument());

    CliArgumentsException unsupportedInspectArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "inspect-rekey-rollback", "--book-file", "book.sqlite", "--explode"
                    }));
    assertEquals("--explode", unsupportedInspectArgument.argument());

    CliArgumentsException missingInspectBookFile =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"inspect-rekey-rollback", "--output", "text"}));
    assertEquals("--book-file", missingInspectBookFile.argument());
  }

  @Test
  void bookPathValidator_rejectsMaintenanceCollisionsAndSharedStandardInput() {
    Path bookFile = Path.of("book.sqlite");
    BookAccess.PassphraseSource keyFile =
        new BookAccess.PassphraseSource.KeyFile(Path.of("book.key"));

    assertEquals(
        "--book-key-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctPaths(
                        bookFile, new BookAccess.PassphraseSource.KeyFile(bookFile), null))
            .argument());
    assertEquals(
        "--request-file",
        assertThrows(
                CliArgumentsException.class,
                () -> CliBookPathValidator.validateDistinctPaths(bookFile, keyFile, bookFile))
            .argument());
    assertEquals(
        "--book-key-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctPaths(
                        bookFile, keyFile, Path.of("book.key")))
            .argument());

    CliArgumentsException requestStdinCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliBookPathValidator.validateStandardInputUsage(
                    BookAccess.PassphraseSource.StandardInput.INSTANCE,
                    Path.of(ProtocolOptions.STDIN_TOKEN)));
    assertEquals("--book-passphrase-stdin", requestStdinCollision.argument());

    assertEquals(
        "--backup-book-file-out",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctBackupPaths(
                        bookFile, keyFile, Path.of("book.key"), Path.of("backup.key")))
            .argument());
    assertEquals(
        "--backup-book-key-file-out",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctBackupPaths(
                        bookFile, keyFile, Path.of("backup/entity.sqlite"), Path.of("book.key")))
            .argument());
    assertEquals(
        "--backup-book-key-file-out",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctBackupPaths(
                        bookFile,
                        keyFile,
                        Path.of("backup/entity.sqlite"),
                        Path.of("backup/entity.sqlite")))
            .argument());
    assertEquals(
        "--backup-book-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctRestorePaths(
                        bookFile, bookFile, Path.of("backup.key")))
            .argument());
    assertEquals(
        "--backup-book-key-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctRestorePaths(
                        bookFile, Path.of("backup.sqlite"), bookFile))
            .argument());

    CliBookPathValidator.validateDistinctBackupPaths(
        bookFile,
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        Path.of("backup/entity.sqlite"),
        Path.of("backup/entity.key"));

    CliArgumentsException replacementStandardInputCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliBookPathValidator.validateRekeyStandardInputUsage(
                    BookAccess.PassphraseSource.StandardInput.INSTANCE,
                    BookAccess.PassphraseSource.StandardInput.INSTANCE));
    assertEquals("--new-book-passphrase-stdin", replacementStandardInputCollision.argument());
  }

  @Test
  void executionPolicy_coversMaintenanceFailureModesAndTypedExitCodes() {
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.BackedUp(
                hint(Path.of("book.sqlite")),
                hint(Path.of("backup.sqlite")),
                hint(Path.of("backup.key")))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    hint(Path.of("backup.sqlite"))))));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Restored(
                hint(Path.of("book.sqlite")),
                hint(Path.of("backup.sqlite")),
                hint(Path.of("backup.key")))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    hint(Path.of("backup.sqlite")), List.of(hint(Path.of("backup.sqlite-wal")))))));
    assertEquals(
        6,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.ArtifactVerificationFailed(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .RESTORED_TARGET,
                    hint(Path.of("book.sqlite")),
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure
                        .PROTECTED_BOOK_VERIFICATION_FAILED))));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Inspected(hint(Path.of("book.sqlite")), List.of())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Deleted(
                hint(Path.of("book.sqlite")), hint(Path.of("book.rekey-rollback.sqlite")))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Rejected(
                new BookMaintenanceRejection.NoRollbackArtifactsFound(
                    hint(Path.of("book.sqlite"))))));
  }

  @Test
  void maintenanceRejectionPayloadMapper_coversEveryHintAndDetailShape() {
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BookHasBlockingArtifacts(
            hint(Path.of("books/entity.sqlite")),
            List.of(
                hint(Path.of("books/entity.sqlite-wal")),
                hint(Path.of("books/entity.sqlite-shm")))),
        "clean closed-copy state",
        CliRejectionJsonModels.BlockingArtifactsDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
            hint(Path.of("backup/entity.sqlite")),
            List.of(hint(Path.of("backup/entity.sqlite-wal")))),
        "Choose one encrypted backup copy",
        CliRejectionJsonModels.BlockingArtifactsDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
            hint(Path.of("books/entity.sqlite")), hint(Path.of("backup/entity.sqlite"))),
        "backup copy path that differs",
        CliRejectionJsonModels.BookAndBackupFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.ArtifactBusy(
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.BACKUP_SOURCE,
            hint(Path.of("backup/entity.sqlite"))),
        "wait for the active maintenance workflow",
        CliRejectionJsonModels.ArtifactBusyDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(
            hint(Path.of("backup/entity.sqlite"))),
        "Choose a new --backup-book-file-out path",
        CliRejectionJsonModels.BackupFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupKeyFileAlreadyExists(hint(Path.of("backup/entity.key"))),
        "Choose a new --backup-book-key-file-out path",
        CliRejectionJsonModels.BackupBookKeyFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.NoRollbackArtifactsFound(hint(Path.of("books/entity.sqlite"))),
        "inspect-rekey-rollback",
        CliRejectionJsonModels.BookFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
            hint(Path.of("books/entity.sqlite")),
            List.of(
                hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                hint(Path.of("books/entity.rekey-rollback-b.sqlite")))),
        "with one explicit --rollback-book-file path",
        CliRejectionJsonModels.RollbackArtifactSelectionDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactNotFound(
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        "Choose an existing rollback artifact path",
        CliRejectionJsonModels.RollbackArtifactDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactNotForBook(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("other/entity.rekey-rollback.sqlite"))),
        "matches FinGrind's canonical rollback naming",
        CliRejectionJsonModels.RollbackArtifactMismatchDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.ArtifactVerificationFailed(
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole.RESTORED_TARGET,
            hint(Path.of("books/entity.sqlite")),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure
                .PROTECTED_BOOK_VERIFICATION_FAILED),
        "matching passphrase source",
        CliRejectionJsonModels.ArtifactVerificationFailureDetails.class);
  }

  @Test
  void maintenanceExitCodes_coverEveryRejectionFamily() {
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.BookHasBlockingArtifacts(
                    hint(Path.of("books/entity.sqlite")),
                    List.of(hint(Path.of("books/entity.sqlite-wal")))))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    hint(Path.of("backup/entity.sqlite")),
                    List.of(hint(Path.of("backup/entity.sqlite-wal")))))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
                    hint(Path.of("books/entity.sqlite")), hint(Path.of("backup/entity.sqlite"))))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.ArtifactBusy(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .BACKUP_SOURCE,
                    hint(Path.of("backup/entity.sqlite"))))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    hint(Path.of("backup/entity.sqlite"))))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.BackupKeyFileAlreadyExists(
                    hint(Path.of("backup/entity.key"))))));
    assertEquals(
        6,
        CliAdministrativeExitCodes.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.ArtifactVerificationFailed(
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole
                        .RESTORED_TARGET,
                    hint(Path.of("books/entity.sqlite")),
                    dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure
                        .PROTECTED_BOOK_VERIFICATION_FAILED))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Rejected(
                new BookMaintenanceRejection.NoRollbackArtifactsFound(
                    hint(Path.of("books/entity.sqlite"))))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Rejected(
                new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                    hint(Path.of("books/entity.sqlite")),
                    List.of(
                        hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback-b.sqlite")))))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Rejected(
                new BookMaintenanceRejection.RollbackArtifactNotFound(
                    hint(Path.of("books/entity.rekey-rollback.sqlite"))))));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyRollbackResult.Rejected(
                new BookMaintenanceRejection.RollbackArtifactNotForBook(
                    hint(Path.of("books/entity.sqlite")),
                    hint(Path.of("other/entity.rekey-rollback.sqlite"))))));
  }

  @Test
  void failureOutputRenderer_rendersMaintenanceRejectionDetailRows() {
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BookFileDetails("/tmp/book.sqlite"),
        "Book file",
        "/tmp/book.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BookAndBackupFileDetails(
            "/tmp/book.sqlite", "/tmp/backup.sqlite"),
        "Book file",
        "/tmp/book.sqlite",
        "Backup file",
        "/tmp/backup.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BlockingArtifactsDetails(
            "/tmp/book.sqlite", List.of("/tmp/book.sqlite-wal", "/tmp/book.sqlite-shm")),
        "Blocking artifacts",
        "/tmp/book.sqlite-wal",
        "/tmp/book.sqlite-shm");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.ArtifactBusyDetails("backup-source", "<redacted>/backup.sqlite"),
        "Artifact role",
        "backup-source",
        "Artifact path",
        "<redacted>/backup.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BackupFileDetails("/tmp/backup.sqlite"),
        "Backup file",
        "/tmp/backup.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BackupBookKeyFileDetails("/tmp/backup.key"),
        "Backup key file",
        "/tmp/backup.key");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.ArtifactVerificationFailureDetails(
            "restored-target", "<redacted>/book.sqlite", "protected-book-verification-failed"),
        "Artifact role",
        "restored-target",
        "Artifact path",
        "<redacted>/book.sqlite",
        "Verification failure",
        "protected-book-verification-failed");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.RollbackArtifactDetails("/tmp/book.rekey-rollback.sqlite"),
        "Rollback artifact",
        "/tmp/book.rekey-rollback.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.RollbackArtifactMismatchDetails(
            "/tmp/book.sqlite", "/tmp/other.rekey-rollback.sqlite"),
        "Book file",
        "/tmp/book.sqlite",
        "Rollback artifact",
        "/tmp/other.rekey-rollback.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.RollbackArtifactSelectionDetails(
            "/tmp/book.sqlite",
            List.of("/tmp/a.rekey-rollback.sqlite", "/tmp/b.rekey-rollback.sqlite")),
        "Rollback artifacts",
        "/tmp/a.rekey-rollback.sqlite",
        "/tmp/b.rekey-rollback.sqlite");
  }

  @Test
  void mutationWriters_renderMaintenanceJsonAndTextVariantsAndRejectCsv() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CliMutationResponseWriter writer =
        new CliMutationResponseWriter(new CliOutputChannel(utf8PrintStream(output)));

    writer.writeBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("backup/entity.sqlite")),
            hint(Path.of("backup/entity.key"))),
        OutputMode.JSON);
    assertEquals(
        publicHintValue(Path.of("books/entity.sqlite")),
        readJson(output).path("payload").path("bookFile").asText().replace('\\', '/'));
    assertEquals(
        publicHintValue(Path.of("backup/entity.sqlite")),
        readJson(output).path("artifacts").get(0).path("path").asText().replace('\\', '/'));
    assertEquals(
        ProtocolArtifactOutput.backupBookFileFormat(),
        readJson(output).path("artifacts").get(0).path("format").asText());
    output.reset();

    writer.writeRestoreBookResult(
        new RestoreBookResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("backup/entity.sqlite")),
            hint(Path.of("backup/entity.key"))),
        OutputMode.JSON);
    assertEquals(
        publicHintValue(Path.of("books/entity.sqlite")),
        readJson(output).path("payload").path("bookFile").asText().replace('\\', '/'));
    assertEquals(
        publicHintValue(Path.of("backup/entity.key")),
        readJson(output).path("artifacts").get(1).path("path").asText().replace('\\', '/'));
    assertEquals(
        ProtocolArtifactOutput.backupBookKeyFileFormat(),
        readJson(output).path("artifacts").get(1).path("format").asText());
    output.reset();

    writer.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(
            hint(Path.of("books/entity.sqlite")),
            List.of(hint(Path.of("books/entity.rekey-rollback.sqlite")))),
        OutputMode.JSON);
    assertEquals(1, readJson(output).path("artifacts").size());
    assertEquals(
        ProtocolArtifactOutput.rollbackBookFileFormat(),
        readJson(output).path("artifacts").get(0).path("format").asText());
    output.reset();

    writer.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.TEXT);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Rollback Artifact Deleted"));
    output.reset();

    writer.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.JSON);
    assertEquals(
        publicHintValue(Path.of("books/entity.rekey-rollback.sqlite")),
        readJson(output).path("artifacts").get(0).path("path").asText().replace('\\', '/'));
    assertEquals(
        ProtocolArtifactOutput.rollbackBookFileFormat(),
        readJson(output).path("artifacts").get(0).path("format").asText());
    output.reset();

    writer.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.TEXT);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Book Restored From Rollback"));
    output.reset();

    writer.writeRestoreBookResult(
        new RestoreBookResult.Rejected(
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                hint(Path.of("backup/entity.sqlite")),
                List.of(hint(Path.of("backup/entity.sqlite-wal"))))),
        OutputMode.JSON);
    assertEquals("backup-source-has-blocking-artifacts", readJson(output).path("code").asText());
    output.reset();

    IllegalArgumentException backupCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeBackupBookResult(
                    new BackupBookResult.BackedUp(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("backup/entity.sqlite")),
                        hint(Path.of("backup/entity.key"))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.BACKUP_BOOK), backupCsv.getMessage());

    IllegalArgumentException restoreCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRestoreBookResult(
                    new RestoreBookResult.Restored(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("backup/entity.sqlite")),
                        hint(Path.of("backup/entity.key"))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RESTORE_BOOK), restoreCsv.getMessage());

    IllegalArgumentException recoverCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRestoreRekeyRollbackResult(
                    new RekeyRollbackResult.Restored(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RESTORE_REKEY_ROLLBACK),
        recoverCsv.getMessage());

    IllegalArgumentException recoverInspectCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeInspectRekeyRollbackResult(
                    new RekeyRollbackResult.Inspected(
                        hint(Path.of("books/entity.sqlite")),
                        List.of(hint(Path.of("books/entity.rekey-rollback.sqlite")))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.INSPECT_REKEY_ROLLBACK),
        recoverInspectCsv.getMessage());

    IllegalArgumentException recoverDeleteCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeDeleteRekeyRollbackResult(
                    new RekeyRollbackResult.Deleted(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.DELETE_REKEY_ROLLBACK),
        recoverDeleteCsv.getMessage());
  }

  @Test
  void inspectRekeyRollbackJson_omitsArtifactsWhenNoRollbackFilesExist() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CliMutationResponseWriter writer =
        new CliMutationResponseWriter(new CliOutputChannel(utf8PrintStream(output)));

    writer.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(hint(Path.of("books/entity.sqlite")), List.of()),
        OutputMode.JSON);

    assertTrue(readJson(output).path("artifacts").isMissingNode());
  }

  @Test
  void mutationOutputRenderer_rendersRollbackRecoveryVariants() {
    String inspectedText =
        CliBookMaintenanceOutputRenderer.renderInspectRekeyRollbackText(
            new RekeyRollbackResult.Inspected(hint(Path.of("books/entity.sqlite")), List.of()));
    assertTrue(inspectedText.contains("Rollback artifacts"));
    assertTrue(inspectedText.contains("(none)"));

    String restoredText =
        CliBookMaintenanceOutputRenderer.renderRestoreRekeyRollbackText(
            new RekeyRollbackResult.Restored(
                hint(Path.of("books/entity.sqlite")),
                hint(Path.of("books/entity.rekey-rollback.sqlite"))));
    assertTrue(restoredText.contains("Book Restored From Rollback"));
  }

  @Test
  void sqliteCliLifecycleWorkflow_routesMaintenanceMethodsThroughFileServices() throws Exception {
    Path tempDirectory = Files.createTempDirectory("fingrind-cli-maintenance-workflow");
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
    Path bookFile = Files.createFile(tempDirectory.resolve("book.sqlite"));
    Files.createFile(tempDirectory.resolve("book.sqlite-wal"));
    Path backupFile = tempDirectory.resolve("backup.sqlite");
    Path backupBookKeyFile = tempDirectory.resolve("backup.key");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            InputStream.nullInputStream(),
            prompt -> {
              throw new AssertionError("interactive prompt should not be used");
            });
    SqliteCliLifecycleWorkflow workflow =
        new SqliteCliLifecycleWorkflow(Clock.systemUTC(), resolver);

    BackupBookResult backupResult =
        workflow
            .backupBook(
                new BookAccess(
                    bookFile, new BookAccess.PassphraseSource.KeyFile(Path.of("book.key"))),
                backupFile,
                backupBookKeyFile)
            .requireAccepted();
    assertInstanceOf(BackupBookResult.Rejected.class, backupResult);

    Path backupSource = Files.createFile(tempDirectory.resolve("source-backup.sqlite"));
    Files.createFile(tempDirectory.resolve("source-backup.sqlite-wal"));
    RestoreBookResult restoreResult =
        workflow
            .restoreBook(tempDirectory.resolve("restored.sqlite"), backupSource, backupBookKeyFile)
            .requireAccepted();
    assertInstanceOf(RestoreBookResult.Rejected.class, restoreResult);

    RekeyRollbackResult inspectResult =
        workflow.inspectRekeyRollback(tempDirectory.resolve("recovery.sqlite")).requireAccepted();
    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(RekeyRollbackResult.Inspected.class, inspectResult);
    assertEquals(hint(tempDirectory.resolve("recovery.sqlite")), inspected.bookFilePath());

    RekeyRollbackResult deleteResult =
        workflow
            .deleteRekeyRollback(
                new BookAccess(
                    tempDirectory.resolve("recovery.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(Path.of("book.key"))),
                null)
            .requireAccepted();
    assertInstanceOf(RekeyRollbackResult.Rejected.class, deleteResult);

    RekeyRollbackResult restoreRollbackResult =
        workflow
            .restoreRekeyRollback(
                tempDirectory.resolve("recovery.sqlite"),
                null,
                new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")))
            .requireAccepted();
    assertInstanceOf(RekeyRollbackResult.Rejected.class, restoreRollbackResult);
  }

  @Test
  void maintenanceJsonDetailModels_rejectEmptyArtifactLists() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.BlockingArtifactsDetails("/tmp/book.sqlite", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliRejectionJsonModels.RollbackArtifactSelectionDetails(
                "/tmp/book.sqlite", List.of()));
  }

  private static void assertRenderedMaintenanceDetails(
      CliRejectionJsonModels.RejectionDetails details, String... fragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "maintenance-rejected", "Maintenance rejected.", "Repair it.", null, details);
    for (String fragment : fragments) {
      assertTrue(rendered.contains(fragment));
    }
  }

  private static void assertMaintenanceEnvelope(
      BookMaintenanceRejection rejection, String expectedHintFragment, Class<?> detailsType) {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejection);
    String hint = envelope.hint();
    assertNotNull(hint);
    assertTrue(hint.contains(expectedHintFragment));
    assertInstanceOf(detailsType, envelope.details());
  }
}
