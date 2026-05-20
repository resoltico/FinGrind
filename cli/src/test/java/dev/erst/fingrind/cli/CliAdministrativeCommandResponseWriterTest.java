package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliResponseWriter}. */
class CliAdministrativeCommandResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeAdministrativeAndWriteSuccesses_supportHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
            Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Key File Generated"));
    outputStream.reset();
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
        OutputMode.HUMAN);
    String openBookHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(openBookHuman.contains("Book Initialized"));
    assertTrue(openBookHuman.contains("Entity"));
    assertTrue(openBookHuman.contains("Acme Studio"));
    assertTrue(openBookHuman.contains("Entity form"));
    assertTrue(openBookHuman.contains("Owner model"));
    assertTrue(openBookHuman.contains("Reporting obligation"));
    assertTrue(openBookHuman.contains("Functional currency"));
    assertTrue(openBookHuman.contains("Fiscal year start"));
    assertTrue(openBookHuman.contains("Accounting basis"));
    outputStream.reset();
    responseWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")),
        new BookAccess.PassphraseSource.KeyFile(Path.of("keys/rotated.key")),
        OutputMode.HUMAN);
    String rekeyHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rekeyHuman.contains("Book Rekeyed"));
    assertTrue(rekeyHuman.contains("Replacement secret source"));
    assertTrue(rekeyHuman.contains("Key file"));
    assertTrue(rekeyHuman.contains("rotated.key"));
    outputStream.reset();
    responseWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Declared(
            CliIoFixtureSupport.declaredAccount(
                "1000",
                "Cash",
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-17T10:15:30Z"))),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Account Declared"));
    outputStream.reset();
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()), OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Period Closed"));
    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Entry Preflight Accepted"));
    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-17"),
            Instant.parse("2026-04-17T10:15:31Z")),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Entry Committed"));
  }

  @Test
  void writeClosePeriodResult_rendersExplicitEmptyClosingPostingSet() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(
            new dev.erst.fingrind.contract.bookkeeping.ClosedPeriod(
                1,
                new dev.erst.fingrind.core.ReportingPeriod(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                new AccountCode("3200"),
                List.of(),
                Instant.parse("2026-04-30T12:00:00Z"),
                List.of())),
        OutputMode.HUMAN);

    String human = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(human.contains("Closing postings"));
    assertTrue(human.contains("(none)"));
    assertTrue(human.contains("No closing movements were required"));
  }

  @Test
  void writeClosePeriodResult_omitsEmptyOutcomeWhenClosingMovementsExist() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()), OutputMode.HUMAN);

    String human = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(human.contains("Closing postings"));
    assertFalse(human.contains("No closing movements were required"));
  }

  @Test
  void writeClosePeriodResult_omitsEmptyOutcomeWhenPostingIdsExistWithoutClosedTotals() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(
            new dev.erst.fingrind.contract.bookkeeping.ClosedPeriod(
                1,
                new dev.erst.fingrind.core.ReportingPeriod(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                new AccountCode("3200"),
                List.of(),
                Instant.parse("2026-04-30T12:00:00Z"),
                List.of(new PostingId("posting-close-1")))),
        OutputMode.HUMAN);

    String human = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(human.contains("Closing postings"));
    assertFalse(human.contains("No closing movements were required"));
  }

  @Test
  void writeAdministrativeAndWriteSuccesses_rejectCsvOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeGenerateBookKeyFileResult(
                new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
                    Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeOpenBookResult(
                Path.of("books/book.sqlite"),
                openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeRekeyBookResult(
                new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeDeclareAccountResult(
                new DeclareAccountResult.Declared(
                    CliIoFixtureSupport.declaredAccount(
                        "1000",
                        "Cash",
                        dev.erst.fingrind.core.AccountType.ASSET,
                        NormalBalance.DEBIT,
                        true,
                        Instant.parse("2026-04-17T10:15:30Z"))),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeClosePeriodResult(
                new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                new PostEntryResult.PreflightAccepted(
                    new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                new PostEntryResult.Committed(
                    new PostingId("posting-1"),
                    new IdempotencyKey("idem-1"),
                    LocalDate.parse("2026-04-17"),
                    Instant.parse("2026-04-17T10:15:31Z")),
                OutputMode.CSV));
  }

  @Test
  void writeAdministrativeAndWriteRejections_supportHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Rejected"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("book-already-initialized"));
    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Idempotency key"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("duplicate-idempotency-key"));
    outputStream.reset();
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                dev.erst.fingrind.core.FinancialPositionLineClassification.RETAINED_EARNINGS,
                List.of())),
        OutputMode.HUMAN);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("closing-equity-account-candidate-missing"));
  }

  @Test
  void writeOpenBookResult_writesSuccessEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), openedBookResult(Instant.parse("2026-04-07T10:15:30Z")));
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertJsonContains(json, "\"status\":\"ok\"");
    assertJsonContains(json, "\"bookFile\"");
    assertJsonContains(json, "\"initializedAt\":\"2026-04-07T10:15:30Z\"");
    assertTrue(json.contains("\"bookIdentity\""));
    assertJsonContains(json, "\"entityName\":\"Acme Studio\"");
    assertJsonContains(json, "\"functionalCurrency\":\"EUR\"");
    assertJsonContains(json, "\"fiscalYearStart\":\"01-01\"");
  }

  @Test
  void writeOpenBookResult_writesAlreadyInitializedAndSchemaConflictRejections() {
    String alreadyInitializedJson =
        openBookRejectedJson(new BookAdministrationRejection.BookAlreadyInitialized());
    String schemaConflictJson =
        openBookRejectedJson(new BookAdministrationRejection.BookContainsSchema());
    assertJsonContains(alreadyInitializedJson, "\"code\":\"book-already-initialized\"");
    assertTrue(alreadyInitializedJson.contains("already initialized"));
    assertJsonContains(schemaConflictJson, "\"code\":\"book-contains-schema\"");
    assertTrue(schemaConflictJson.contains("already contains schema objects"));
  }

  @Test
  void writeRekeyBookResult_writesSuccessAndRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        new BookAccess.PassphraseSource.KeyFile(Path.of("keys").resolve("rotated.key")));
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(successJson, "\"status\":\"ok\"");
    assertJsonContains(successJson, "\"bookFile\"");
    assertJsonContains(successJson, "\"replacementPassphraseSource\":\"key-file\"");
    assertTrue(successJson.contains("\"replacementBookKeyFile\""));
    ByteArrayOutputStream rejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter rejectionWriter = new CliResponseWriter(utf8PrintStream(rejectionOutput));
    rejectionWriter.writeRekeyBookResult(
        new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE);
    String rejectionJson = rejectionOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(rejectionJson, "\"status\":\"rejected\"");
    assertJsonContains(rejectionJson, "\"code\":\"administration-book-not-initialized\"");
  }

  @Test
  void writeRekeyBookResult_supportsNonFileReplacementSecretSources() {
    ByteArrayOutputStream standardInputHumanOutput = new ByteArrayOutputStream();
    CliResponseWriter standardInputHumanWriter =
        new CliResponseWriter(utf8PrintStream(standardInputHumanOutput));
    standardInputHumanWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        OutputMode.HUMAN);
    String standardInputHuman = standardInputHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(standardInputHuman.contains("Standard input"));
    assertFalse(standardInputHuman.contains("Replacement key file"));

    ByteArrayOutputStream interactivePromptHumanOutput = new ByteArrayOutputStream();
    CliResponseWriter interactivePromptHumanWriter =
        new CliResponseWriter(utf8PrintStream(interactivePromptHumanOutput));
    interactivePromptHumanWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        OutputMode.HUMAN);
    String interactivePromptHuman = interactivePromptHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(interactivePromptHuman.contains("Interactive prompt"));
    assertFalse(interactivePromptHuman.contains("Replacement key file"));

    ByteArrayOutputStream standardInputJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter standardInputJsonWriter =
        new CliResponseWriter(utf8PrintStream(standardInputJsonOutput));
    standardInputJsonWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        BookAccess.PassphraseSource.StandardInput.INSTANCE);
    String standardInputJson = standardInputJsonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(standardInputJson, "\"replacementPassphraseSource\":\"standard-input\"");
    assertFalse(standardInputJson.contains("\"replacementBookKeyFile\""));

    ByteArrayOutputStream interactivePromptJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter interactivePromptJsonWriter =
        new CliResponseWriter(utf8PrintStream(interactivePromptJsonOutput));
    interactivePromptJsonWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE);
    String interactivePromptJson = interactivePromptJsonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(
        interactivePromptJson, "\"replacementPassphraseSource\":\"interactive-prompt\"");
    assertFalse(interactivePromptJson.contains("\"replacementBookKeyFile\""));
  }

  @Test
  void writeMaintenanceResults_supportSuccessEnvelopesAndHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("backup/entity.sqlite")),
            hint(Path.of("backup/entity.key"))),
        OutputMode.HUMAN);
    String backupHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(backupHuman.contains("Book Backed Up"));
    assertTrue(backupHuman.contains("Backup file"));
    assertTrue(backupHuman.contains("entity.sqlite"));
    outputStream.reset();

    responseWriter.writeRestoreBookResult(
        new RestoreBookResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("backup/entity.sqlite")),
            hint(Path.of("backup/entity.key"))),
        OutputMode.HUMAN);
    String restoreHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(restoreHuman.contains("Book Restored"));
    assertTrue(restoreHuman.contains("Book key file"));
    outputStream.reset();

    responseWriter.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(
            hint(Path.of("books/entity.sqlite")),
            List.of(
                hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                hint(Path.of("books/entity.rekey-rollback-b.sqlite")))),
        OutputMode.HUMAN);
    String inspectHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(inspectHuman.contains("Rekey Rollback Artifacts"));
    assertTrue(inspectHuman.contains("rollback-a"));
    assertTrue(inspectHuman.contains("rollback-b"));
    outputStream.reset();

    responseWriter.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.JSON);
    String restoreJson = outputStream.toString(StandardCharsets.UTF_8);
    assertJsonContains(restoreJson, "\"status\":\"ok\"");
    assertJsonContains(restoreJson, "\"rollbackArtifact\"");
  }

  @Test
  void writeMaintenanceRejections_emitStructuredDetails() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBackupBookResult(
        new BackupBookResult.Rejected(
            new BookMaintenanceRejection.BookHasBlockingArtifacts(
                hint(Path.of("books/entity.sqlite")),
                List.of(
                    hint(Path.of("books/entity.sqlite-wal")),
                    hint(Path.of("books/entity.sqlite-shm"))))),
        OutputMode.HUMAN);
    String human = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(human.contains("Rejected"));
    assertTrue(human.contains("book-has-blocking-artifacts"));
    assertTrue(human.contains("Blocking artifacts"));
    outputStream.reset();

    responseWriter.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Rejected(
            new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                hint(Path.of("books/entity.sqlite")),
                List.of(
                    hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                    hint(Path.of("books/entity.rekey-rollback-b.sqlite"))))),
        OutputMode.JSON);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertJsonContains(json, "\"status\":\"rejected\"");
    assertJsonContains(json, "\"code\":\"rollback-artifact-selection-required\"");
    assertTrue(
        readJson(outputStream)
            .path("details")
            .path("bookFile")
            .asText()
            .replace('\\', '/')
            .endsWith("<redacted>/entity.sqlite"));
    assertEquals(2, readJson(outputStream).path("details").path("rollbackArtifacts").size());
  }

  @Test
  void writeDeleteRekeyRollbackResult_writesSuccessEnvelope() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.JSON);

    assertEquals("ok", readJson(outputStream).path("status").stringValue());
    assertTrue(readJson(outputStream).path("payload").has("rollbackArtifact"));
  }

  @Test
  void writeRekeyRollbackRestoreAndDelete_supportHumanAndRejectCsv() {
    ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
    CliResponseWriter restoreWriter = new CliResponseWriter(utf8PrintStream(restoreOutput));
    restoreWriter.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.HUMAN);
    assertTrue(
        restoreOutput.toString(StandardCharsets.UTF_8).contains("Book Restored From Rollback"));

    ByteArrayOutputStream deleteOutput = new ByteArrayOutputStream();
    CliResponseWriter deleteWriter = new CliResponseWriter(utf8PrintStream(deleteOutput));
    deleteWriter.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.HUMAN);
    assertTrue(deleteOutput.toString(StandardCharsets.UTF_8).contains("Rollback Artifact Deleted"));

    IllegalArgumentException restoreCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                restoreWriter.writeRestoreRekeyRollbackResult(
                    new RekeyRollbackResult.Restored(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.CSV));
    IllegalArgumentException deleteCsv =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                deleteWriter.writeDeleteRekeyRollbackResult(
                    new RekeyRollbackResult.Deleted(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.CSV));

    assertTrue(
        java.util.Objects.requireNonNull(restoreCsv.getMessage())
            .contains("restore-rekey-rollback"));
    assertTrue(
        java.util.Objects.requireNonNull(deleteCsv.getMessage()).contains("delete-rekey-rollback"));
  }

  @Test
  void writeRekeyRollbackRestoreAndDelete_writeMaintenanceRejections() throws Exception {
    ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
    CliResponseWriter restoreWriter = new CliResponseWriter(utf8PrintStream(restoreOutput));
    restoreWriter.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Rejected(
            new BookMaintenanceRejection.RollbackArtifactNotForBook(
                hint(Path.of("books/entity.sqlite")),
                hint(Path.of("books/other.rekey-rollback.sqlite")))),
        OutputMode.JSON);

    assertEquals("rejected", readJson(restoreOutput).path("status").stringValue());
    assertEquals(
        "rollback-artifact-not-for-book", readJson(restoreOutput).path("code").stringValue());

    ByteArrayOutputStream deleteOutput = new ByteArrayOutputStream();
    CliResponseWriter deleteWriter = new CliResponseWriter(utf8PrintStream(deleteOutput));
    deleteWriter.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Rejected(
            new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                hint(Path.of("books/entity.sqlite")),
                List.of(
                    hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                    hint(Path.of("books/entity.rekey-rollback-b.sqlite"))))),
        OutputMode.HUMAN);

    String human = deleteOutput.toString(StandardCharsets.UTF_8);
    assertTrue(human.contains("Rejected"));
    assertTrue(human.contains("rollback-artifact-selection-required"));
  }

  @Test
  void writeRekeyRollbackResult_rejectsUnexpectedResultShapes() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    IllegalArgumentException inspectUnexpected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                responseWriter.writeInspectRekeyRollbackResult(
                    new RekeyRollbackResult.Restored(
                        hint(Path.of("books/entity.sqlite")),
                        hint(Path.of("books/entity.rekey-rollback.sqlite"))),
                    OutputMode.JSON));
    assertTrue(
        java.util.Objects.requireNonNull(inspectUnexpected.getMessage())
            .contains("Inspect rekey rollback"));

    IllegalArgumentException restoreUnexpected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                responseWriter.writeRestoreRekeyRollbackResult(
                    new RekeyRollbackResult.Inspected(
                        hint(Path.of("books/entity.sqlite")),
                        List.of(hint(Path.of("books/entity.rekey-rollback.sqlite")))),
                    OutputMode.JSON));
    assertTrue(
        java.util.Objects.requireNonNull(restoreUnexpected.getMessage())
            .contains("Restore rekey rollback"));

    IllegalArgumentException deleteUnexpected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                responseWriter.writeDeleteRekeyRollbackResult(
                    new RekeyRollbackResult.Inspected(
                        hint(Path.of("books/entity.sqlite")),
                        List.of(hint(Path.of("books/entity.rekey-rollback.sqlite")))),
                    OutputMode.JSON));
    assertTrue(
        java.util.Objects.requireNonNull(deleteUnexpected.getMessage())
            .contains("Delete rekey rollback"));
  }

  @Test
  void writeDeclareAccountAndListAccountsResults_writeSuccessAndRejectionEnvelopes() {
    DeclaredAccount declaredAccount =
        CliIoFixtureSupport.declaredAccount(
            "1000",
            "Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    ByteArrayOutputStream declareSuccessOutput = new ByteArrayOutputStream();
    CliResponseWriter declareSuccessWriter =
        new CliResponseWriter(utf8PrintStream(declareSuccessOutput));
    declareSuccessWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Declared(declaredAccount));
    ByteArrayOutputStream listSuccessOutput = new ByteArrayOutputStream();
    CliResponseWriter listSuccessWriter = new CliResponseWriter(utf8PrintStream(listSuccessOutput));
    listSuccessWriter.writeListAccountsResult(
        new ListAccountsResult.Listed(
            accountPage(java.util.List.of(declaredAccount), 50, java.util.Optional.empty())));
    String declareSuccessJson = declareSuccessOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(declareSuccessJson, "\"accountName\":\"Cash\"");
    assertJsonContains(declareSuccessJson, "\"declaredAt\":\"2026-04-07T10:15:30Z\"");
    String listSuccessJson = listSuccessOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(listSuccessJson, "\"limit\":50");
    assertFalse(listSuccessJson.contains("\"nextCursor\""));
    assertJsonContains(listSuccessJson, "\"accountName\":\"Cash\"");
    ByteArrayOutputStream declareRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter declareRejectionWriter =
        new CliResponseWriter(utf8PrintStream(declareRejectionOutput));
    declareRejectionWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));
    ByteArrayOutputStream declareConflictOutput = new ByteArrayOutputStream();
    CliResponseWriter declareConflictWriter =
        new CliResponseWriter(utf8PrintStream(declareConflictOutput));
    declareConflictWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.AccountRoleConflict(
                new AccountCode("1000"),
                dev.erst.fingrind.core.AccountRole.ORDINARY,
                dev.erst.fingrind.core.AccountRole.CONTRA)));
    ByteArrayOutputStream listRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listRejectionOutput));
    listRejectionWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    assertJsonContains(declareRejectionOutput, "\"code\":\"administration-book-not-initialized\"");
    String declareConflictJson = declareConflictOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(declareConflictJson, "\"code\":\"account-role-conflict\"");
    assertJsonContains(declareConflictJson, "\"accountCode\":\"1000\"");
    assertJsonContains(declareConflictJson, "\"existingAccountRole\":\"ORDINARY\"");
    assertJsonContains(declareConflictJson, "\"requestedAccountRole\":\"CONTRA\"");
    assertTrue(declareConflictJson.contains("already exists with account role"));
    assertJsonContains(listRejectionOutput, "\"code\":\"query-book-not-initialized\"");
  }

  @Test
  void writeClosePeriodResult_writesSuccessAndDetailedRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()), OutputMode.JSON);
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(successJson, "\"status\":\"ok\"");
    assertJsonContains(successJson, "\"closeOrder\":1");
    assertJsonContains(successJson, "\"closingPostingIds\":[\"posting-close-1\"]");

    ByteArrayOutputStream missingOutput = new ByteArrayOutputStream();
    CliResponseWriter missingWriter = new CliResponseWriter(utf8PrintStream(missingOutput));
    missingWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                dev.erst.fingrind.core.FinancialPositionLineClassification.RETAINED_EARNINGS,
                List.of(new AccountCode("3200")))),
        OutputMode.JSON);
    assertJsonContains(missingOutput, "\"code\":\"closing-equity-account-candidate-missing\"");
    assertJsonContains(
        missingOutput, "\"requiredFinancialPositionLineClassification\":\"RETAINED_EARNINGS\"");

    ByteArrayOutputStream horizonOutput = new ByteArrayOutputStream();
    CliResponseWriter horizonWriter = new CliResponseWriter(utf8PrintStream(horizonOutput));
    horizonWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.PeriodCloseMustStartAt(LocalDate.parse("2026-04-01"))),
        OutputMode.JSON);
    String horizonJson = horizonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(horizonJson, "\"code\":\"period-close-must-start-at\"");
    assertJsonContains(horizonJson, "\"requiredEffectiveDateFrom\":\"2026-04-01\"");

    ByteArrayOutputStream typeConflictOutput = new ByteArrayOutputStream();
    CliResponseWriter typeConflictWriter =
        new CliResponseWriter(utf8PrintStream(typeConflictOutput));
    typeConflictWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("3200"),
                dev.erst.fingrind.core.AccountType.EQUITY,
                dev.erst.fingrind.core.AccountType.LIABILITY)),
        OutputMode.JSON);
    String typeConflictJson = typeConflictOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(typeConflictJson, "\"existingAccountType\":\"EQUITY\"");
    assertJsonContains(typeConflictJson, "\"requestedAccountType\":\"LIABILITY\"");

    ByteArrayOutputStream ambiguousOutput = new ByteArrayOutputStream();
    CliResponseWriter ambiguousWriter = new CliResponseWriter(utf8PrintStream(ambiguousOutput));
    ambiguousWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
                dev.erst.fingrind.core.FinancialPositionLineClassification.OTHER_EQUITY,
                List.of(new AccountCode("3200"), new AccountCode("3210")))),
        OutputMode.JSON);
    assertJsonContains(ambiguousOutput, "\"code\":\"closing-equity-account-candidate-ambiguous\"");
    assertEquals(
        2,
        CliExecutionPolicy.exitCodeFor(
            new ClosePeriodResult.Rejected(
                new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
                    dev.erst.fingrind.core.FinancialPositionLineClassification.RETAINED_EARNINGS,
                    List.of()))));
  }

  @Test
  void writePostingRejections_includesStructuredCloseAndReversalDetails() {
    String closedPeriodJson =
        rejectedJson(
            new PostingRejection.ClosedPeriodViolation(
                LocalDate.parse("2026-04-30"), LocalDate.parse("2026-05-01")));
    assertJsonContains(closedPeriodJson, "\"closedThroughEffectiveDate\":\"2026-04-30\"");
    assertJsonContains(closedPeriodJson, "\"attemptedEffectiveDate\":\"2026-05-01\"");

    String retainedEarningsJson =
        rejectedJson(new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3200")));
    assertJsonContains(retainedEarningsJson, "\"accountCode\":\"3200\"");

    String missingPriorPostingJson =
        rejectedJson(new PostingRejection.ReversalTargetNotFound(new PostingId("posting-9")));
    assertJsonContains(missingPriorPostingJson, "\"priorPostingId\":\"posting-9\"");

    String existingReversalJson =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-8")));
    assertJsonContains(existingReversalJson, "\"priorPostingId\":\"posting-8\"");

    String negateTargetJson =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-7")));
    assertJsonContains(negateTargetJson, "\"priorPostingId\":\"posting-7\"");
  }
}
