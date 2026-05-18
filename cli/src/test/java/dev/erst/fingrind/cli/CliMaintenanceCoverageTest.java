package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
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
    RecoverRekey defaultInspect =
        assertInstanceOf(
            RecoverRekey.class,
            CliArguments.parse(new String[] {"recover-rekey", "--book-file", "book.sqlite"}));
    assertEquals(Path.of("book.sqlite"), defaultInspect.bookFilePath());
    assertEquals(RekeyRecoveryAction.INSPECT, defaultInspect.action());
    assertNull(defaultInspect.rollbackArtifactPath());
    assertEquals(OutputMode.JSON, defaultInspect.outputMode());

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
                  "--backup-book-key-file",
                  "backup/entity.key"
                }));
    assertEquals(Path.of("backup/entity.sqlite"), backupBook.backupFilePath());

    BackupBook backupBookWithHumanOutput =
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
                  "--backup-book-key-file",
                  "backup/entity.key",
                  "--output",
                  "human"
                }));
    assertEquals(OutputMode.HUMAN, backupBookWithHumanOutput.outputMode());

    RestoreBook restoreBook =
        assertInstanceOf(
            RestoreBook.class,
            CliArguments.parse(
                new String[] {
                  "restore-book",
                  "--book-file",
                  "book.sqlite",
                  "--backup-file",
                  "backup/entity.sqlite",
                  "--backup-book-key-file",
                  "backup/entity.key",
                  "--output",
                  "human"
                }));
    assertEquals(OutputMode.HUMAN, restoreBook.outputMode());

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
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-file", missingBackupFile.argument());
    assertEquals("A --backup-file argument is required.", missingBackupFile.getMessage());

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
                      "--backup-file",
                      "backup/entity.sqlite"
                    }));
    assertEquals("--backup-book-key-file", missingBackupKeyFile.argument());

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
                      "--backup-file",
                      "backup/entity.sqlite",
                      "--backup-file",
                      "backup/entity-2.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-file", duplicateBackupFile.argument());

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
                      "--backup-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key",
                      "--backup-book-key-file",
                      "backup/entity-2.key"
                    }));
    assertEquals("--backup-book-key-file", duplicateBackupKeyFile.argument());

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
                      "--backup-file",
                      "backup/entity.sqlite",
                      "--backup-book-key-file",
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
                      "--backup-file",
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
    assertEquals("--backup-file", restoreMissingBackupFile.argument());

    CliArgumentsException restoreMissingBackupKeyFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-file",
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
                      "--backup-file",
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
                      "--backup-file",
                      "backup/entity.sqlite",
                      "--backup-file",
                      "backup/entity-2.sqlite",
                      "--backup-book-key-file",
                      "backup/entity.key"
                    }));
    assertEquals("--backup-file", duplicateRestoreBackupFile.argument());

    CliArgumentsException duplicateRestoreBackupKeyFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-file",
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
                      "--backup-file",
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
                      "recover-rekey",
                      "--book-file",
                      "book.sqlite",
                      "--rollback-file",
                      "rollback-a.sqlite",
                      "--rollback-file",
                      "rollback-b.sqlite"
                    }));
    assertEquals("--rollback-file", duplicateRollbackFile.argument());

    CliArgumentsException invalidRecoveryAction =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "recover-rekey", "--book-file", "book.sqlite", "--recovery-action", "explode"
                    }));
    assertEquals("--recovery-action", invalidRecoveryAction.argument());
    String invalidRecoveryMessage = invalidRecoveryAction.getMessage();
    assertNotNull(invalidRecoveryMessage);
    assertTrue(invalidRecoveryMessage.contains("Unsupported rekey recovery action"));

    RecoverRekey recoverWithHumanOutput =
        assertInstanceOf(
            RecoverRekey.class,
            CliArguments.parse(
                new String[] {"recover-rekey", "--book-file", "book.sqlite", "--output", "human"}));
    assertEquals(OutputMode.HUMAN, recoverWithHumanOutput.outputMode());

    CliArgumentsException duplicateRecoverBookFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "recover-rekey", "--book-file", "book.sqlite", "--book-file", "book-2.sqlite"
                    }));
    assertEquals("--book-file", duplicateRecoverBookFile.argument());

    CliArgumentsException unsupportedRecoverArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"recover-rekey", "--book-file", "book.sqlite", "--explode"}));
    assertEquals("--explode", unsupportedRecoverArgument.argument());

    CliArgumentsException missingRecoverBookFile =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"recover-rekey", "--output", "human"}));
    assertEquals("--book-file", missingRecoverBookFile.argument());
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
        "--backup-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctBackupPaths(
                        bookFile, keyFile, Path.of("book.key"), Path.of("backup.key")))
            .argument());
    assertEquals(
        "--backup-book-key-file",
        assertThrows(
                CliArgumentsException.class,
                () ->
                    CliBookPathValidator.validateDistinctBackupPaths(
                        bookFile, keyFile, Path.of("backup/entity.sqlite"), Path.of("book.key")))
            .argument());
    assertEquals(
        "--backup-book-key-file",
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
        "--backup-file",
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
    assertEquals(
        "--replacement-book-passphrase-stdin", replacementStandardInputCollision.argument());
  }

  @Test
  void executionPolicy_coversMaintenanceFailureModesAndTypedExitCodes() {
    assertEquals(
        CliOutputModeDefaults.defaultDiscoveryOutputMode(),
        CliExecutionPolicy.inferredFailureOutputMode(new String[] {"help"}));
    assertEquals(
        CliOutputModeDefaults.defaultDiscoveryOutputMode(),
        CliExecutionPolicy.inferredFailureOutputMode(new String[] {"version"}));
    assertEquals(
        CliOutputModeDefaults.defaultDiscoveryOutputMode(),
        CliExecutionPolicy.inferredFailureOutputMode(new String[] {"capabilities"}));
    assertEquals(
        OutputMode.JSON,
        CliExecutionPolicy.inferredFailureOutputMode(
            new String[] {OperationId.PRINT_REQUEST_TEMPLATE.wireName()}));
    assertEquals(
        OutputMode.JSON,
        CliExecutionPolicy.inferredFailureOutputMode(
            new String[] {"backup-book", "--output", "csv"}));

    assertEquals(
        0,
        CliExecutionPolicy.exitCodeFor(
            new BackupBookResult.BackedUp(
                Path.of("book.sqlite"), Path.of("backup.sqlite"), Path.of("backup.key"))));
    assertEquals(
        2,
        CliExecutionPolicy.exitCodeFor(
            new BackupBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite")))));
    assertEquals(
        0,
        CliExecutionPolicy.exitCodeFor(
            new RestoreBookResult.Restored(
                Path.of("book.sqlite"), Path.of("backup.sqlite"), Path.of("backup.key"))));
    assertEquals(
        2,
        CliExecutionPolicy.exitCodeFor(
            new RestoreBookResult.Rejected(
                new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    Path.of("backup.sqlite"), List.of(Path.of("backup.sqlite-wal"))))));
    assertEquals(
        0,
        CliExecutionPolicy.exitCodeFor(
            new RecoverRekeyResult.Inspected(Path.of("book.sqlite"), List.of())));
    assertEquals(
        0,
        CliExecutionPolicy.exitCodeFor(
            new RecoverRekeyResult.Deleted(
                Path.of("book.sqlite"), Path.of("book.rekey-rollback.sqlite"))));
    assertEquals(
        2,
        CliExecutionPolicy.exitCodeFor(
            new RecoverRekeyResult.Rejected(
                new BookMaintenanceRejection.NoRollbackArtifactsFound(Path.of("book.sqlite")))));
  }

  @Test
  void maintenanceRejectionPayloadMapper_coversEveryHintAndDetailShape() {
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BookHasBlockingArtifacts(
            Path.of("books/entity.sqlite"),
            List.of(Path.of("books/entity.sqlite-wal"), Path.of("books/entity.sqlite-shm"))),
        "clean closed-copy state",
        CliRejectionJsonModels.BlockingArtifactsDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
            Path.of("backup/entity.sqlite"), List.of(Path.of("backup/entity.sqlite-wal"))),
        "Choose one encrypted backup copy",
        CliRejectionJsonModels.BlockingArtifactsDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupDestinationAlreadyExists(
            Path.of("backup/entity.sqlite")),
        "Choose a new --backup-file path",
        CliRejectionJsonModels.BackupFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.BackupKeyFileAlreadyExists(Path.of("backup/entity.key")),
        "Choose a new --backup-book-key-file path",
        CliRejectionJsonModels.BackupBookKeyFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.NoRollbackArtifactsFound(Path.of("books/entity.sqlite")),
        "without mutation flags",
        CliRejectionJsonModels.BookFileDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
            Path.of("books/entity.sqlite"),
            List.of(
                Path.of("books/entity.rekey-rollback-a.sqlite"),
                Path.of("books/entity.rekey-rollback-b.sqlite"))),
        "with one explicit --rollback-file path",
        CliRejectionJsonModels.RollbackArtifactSelectionDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactNotFound(
            Path.of("books/entity.rekey-rollback.sqlite")),
        "Choose an existing rollback artifact path",
        CliRejectionJsonModels.RollbackArtifactDetails.class);
    assertMaintenanceEnvelope(
        new BookMaintenanceRejection.RollbackArtifactNotForBook(
            Path.of("books/entity.sqlite"), Path.of("other/entity.rekey-rollback.sqlite")),
        "matches FinGrind's canonical rollback naming",
        CliRejectionJsonModels.RollbackArtifactMismatchDetails.class);
  }

  @Test
  void failureOutputRenderer_rendersMaintenanceRejectionDetailRows() {
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BookFileDetails("/tmp/book.sqlite"),
        "Book file",
        "/tmp/book.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BlockingArtifactsDetails(
            "/tmp/book.sqlite", List.of("/tmp/book.sqlite-wal", "/tmp/book.sqlite-shm")),
        "Blocking artifacts",
        "/tmp/book.sqlite-wal",
        "/tmp/book.sqlite-shm");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BackupFileDetails("/tmp/backup.sqlite"),
        "Backup file",
        "/tmp/backup.sqlite");
    assertRenderedMaintenanceDetails(
        new CliRejectionJsonModels.BackupBookKeyFileDetails("/tmp/backup.key"),
        "Backup key file",
        "/tmp/backup.key");
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
  void mutationWriters_renderMaintenanceJsonAndHumanVariantsAndRejectCsv() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CliMutationResponseWriter writer =
        new CliMutationResponseWriter(new CliOutputChannel(utf8PrintStream(output)));

    writer.writeBackupBookResult(
        new BackupBookResult.BackedUp(
            Path.of("books/entity.sqlite"),
            Path.of("backup/entity.sqlite"),
            Path.of("backup/entity.key")),
        OutputMode.JSON);
    assertPortablePathSuffix(
        readJson(output).path("payload").path("backupFile").asText(), "backup/entity.sqlite");
    output.reset();

    writer.writeRestoreBookResult(
        new RestoreBookResult.Restored(
            Path.of("books/entity.sqlite"),
            Path.of("backup/entity.sqlite"),
            Path.of("backup/entity.key")),
        OutputMode.JSON);
    assertPortablePathSuffix(
        readJson(output).path("payload").path("backupBookKeyFile").asText(), "backup/entity.key");
    output.reset();

    writer.writeRecoverRekeyResult(
        new RecoverRekeyResult.Inspected(
            Path.of("books/entity.sqlite"), List.of(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.JSON);
    assertEquals(1, readJson(output).path("payload").path("rollbackArtifacts").size());
    output.reset();

    writer.writeRecoverRekeyResult(
        new RecoverRekeyResult.Deleted(
            Path.of("books/entity.sqlite"), Path.of("books/entity.rekey-rollback.sqlite")),
        OutputMode.HUMAN);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Rollback Artifact Deleted"));
    output.reset();

    writer.writeRecoverRekeyResult(
        new RecoverRekeyResult.Deleted(
            Path.of("books/entity.sqlite"), Path.of("books/entity.rekey-rollback.sqlite")),
        OutputMode.JSON);
    assertEquals("delete", readJson(output).path("payload").path("action").asText());
    output.reset();

    writer.writeRecoverRekeyResult(
        new RecoverRekeyResult.Restored(
            Path.of("books/entity.sqlite"), Path.of("books/entity.rekey-rollback.sqlite")),
        OutputMode.HUMAN);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Book Restored From Rollback"));
    output.reset();

    writer.writeRestoreBookResult(
        new RestoreBookResult.Rejected(
            new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                Path.of("backup/entity.sqlite"), List.of(Path.of("backup/entity.sqlite-wal")))),
        OutputMode.JSON);
    assertEquals("backup-source-has-blocking-artifacts", readJson(output).path("code").asText());
    output.reset();

    IllegalArgumentException backupCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeBackupBookResult(
                    new BackupBookResult.BackedUp(
                        Path.of("books/entity.sqlite"),
                        Path.of("backup/entity.sqlite"),
                        Path.of("backup/entity.key")),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.BACKUP_BOOK), backupCsv.getMessage());

    IllegalArgumentException restoreCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRestoreBookResult(
                    new RestoreBookResult.Restored(
                        Path.of("books/entity.sqlite"),
                        Path.of("backup/entity.sqlite"),
                        Path.of("backup/entity.key")),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RESTORE_BOOK), restoreCsv.getMessage());

    IllegalArgumentException recoverCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRecoverRekeyResult(
                    new RecoverRekeyResult.Restored(
                        Path.of("books/entity.sqlite"),
                        Path.of("books/entity.rekey-rollback.sqlite")),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RECOVER_REKEY), recoverCsv.getMessage());

    IllegalArgumentException recoverInspectCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRecoverRekeyResult(
                    new RecoverRekeyResult.Inspected(
                        Path.of("books/entity.sqlite"),
                        List.of(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RECOVER_REKEY),
        recoverInspectCsv.getMessage());

    IllegalArgumentException recoverDeleteCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                writer.writeRecoverRekeyResult(
                    new RecoverRekeyResult.Deleted(
                        Path.of("books/entity.sqlite"),
                        Path.of("books/entity.rekey-rollback.sqlite")),
                    OutputMode.CSV));
    assertEquals(
        CliOperationText.unsupportedCsvOutput(OperationId.RECOVER_REKEY),
        recoverDeleteCsv.getMessage());
  }

  @Test
  void mutationOutputRenderer_rendersRollbackRecoveryVariants() {
    String inspectedHuman =
        CliMutationOutputRenderer.renderRecoverRekeyInspectionHuman(
            new RecoverRekeyResult.Inspected(Path.of("books/entity.sqlite"), List.of()));
    assertTrue(inspectedHuman.contains("Rollback artifacts"));
    assertTrue(inspectedHuman.contains("(none)"));

    String restoredHuman =
        CliMutationOutputRenderer.renderRecoverRekeyRestoredHuman(
            new RecoverRekeyResult.Restored(
                Path.of("books/entity.sqlite"), Path.of("books/entity.rekey-rollback.sqlite")));
    assertTrue(restoredHuman.contains("Book Restored From Rollback"));
  }

  @Test
  void sqliteCliBookWorkflow_routesMaintenanceMethodsThroughFileServices() throws Exception {
    Path tempDirectory = Files.createTempDirectory("fingrind-cli-maintenance-workflow");
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
    SqliteCliBookWorkflow workflow = new SqliteCliBookWorkflow(Clock.systemUTC(), resolver);

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

    RecoverRekeyResult inspectResult =
        workflow
            .recoverRekey(
                tempDirectory.resolve("recovery.sqlite"), RekeyRecoveryAction.INSPECT, null)
            .requireAccepted();
    RecoverRekeyResult.Inspected inspected =
        assertInstanceOf(RecoverRekeyResult.Inspected.class, inspectResult);
    assertEquals(
        tempDirectory.resolve("recovery.sqlite").toAbsolutePath().normalize(),
        inspected.bookFilePath());
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
        CliFailureOutputRenderer.renderRejectedHuman(
            "maintenance-rejected", "Maintenance rejected.", "Repair it.", null, details);
    for (String fragment : fragments) {
      assertTrue(rendered.contains(fragment));
    }
  }

  private static void assertMaintenanceEnvelope(
      BookMaintenanceRejection rejection, String expectedHintFragment, Class<?> detailsType) {
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejection);
    String hint = envelope.hint();
    assertNotNull(hint);
    assertTrue(hint.contains(expectedHintFragment));
    assertInstanceOf(detailsType, envelope.details());
  }

  private static void assertPortablePathSuffix(String renderedPath, String expectedSuffix) {
    assertTrue(
        renderedPath.replace('\\', '/').endsWith(expectedSuffix),
        () -> "Expected path ending " + expectedSuffix + " but was " + renderedPath);
  }
}
