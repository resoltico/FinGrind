package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
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
  void writeAdministrativeSuccesses_publishTightenedParentDirectoriesWhenPresent()
      throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliAdministrativeMutationResponseWriter responseWriter =
        new CliAdministrativeMutationResponseWriter(outputChannel(outputStream));
    Path tightenedKeyParent = Path.of("keys");
    Path tightenedBookParent = Path.of("books");

    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile(
            Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
        List.of(tightenedKeyParent),
        OutputMode.JSON);
    var generatedEnvelope = readJson(outputStream);
    assertEquals(
        CliPublicPaths.absoluteValue(tightenedKeyParent),
        generatedEnvelope.path("payload").path("tightenedParentDirectories").get(0).stringValue());

    outputStream.reset();
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        List.of(tightenedBookParent),
        openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
        OutputMode.TEXT);
    String openBookText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(openBookText.contains("Tightened parent directory"), openBookText);
    assertTrue(
        openBookText.contains(CliPublicPaths.redactedValue(tightenedBookParent)), openBookText);
  }

  @Test
  void writeAdministrativeAndWriteSuccesses_supportTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile(
            Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
        OutputMode.TEXT);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Key File Generated"));
    outputStream.reset();
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
        OutputMode.TEXT);
    String openBookText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(openBookText.contains("Book Initialized"));
    assertTrue(openBookText.contains("Entity"));
    assertTrue(openBookText.contains("Acme Studio"));
    assertTrue(openBookText.contains("Functional currency"));
    assertTrue(openBookText.contains("Fiscal year start"));
    outputStream.reset();
    responseWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")),
        Path.of("keys/rotated.key"),
        OutputMode.TEXT);
    String rekeyText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rekeyText.contains("Book Rekeyed"));
    assertTrue(rekeyText.contains("New book key file"));
    assertTrue(rekeyText.contains("rotated.key"));
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
        OutputMode.TEXT);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Account Declared"));
    outputStream.reset();
    responseWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Swept(CliFixtureSupport.sampleSweptInterimResult()),
        OutputMode.TEXT);
    String periodClosedText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(periodClosedText.contains("Interim Result Swept"));
    assertTrue(periodClosedText.contains("Reporting period"));
    outputStream.reset();
    responseWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Closed(CliFixtureSupport.sampleClosedFiscalYear(), false),
        OutputMode.TEXT);
    String fiscalYearCloseText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(fiscalYearCloseText.contains("Fiscal Year Closed"));
    assertTrue(fiscalYearCloseText.contains("Capital account"));
    assertTrue(fiscalYearCloseText.contains("Retained accumulated account"));
    outputStream.reset();
    responseWriter.writePostEntryResult(
        CliPostEntryResultFixtures.preflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
        OutputMode.TEXT);
    String preflightText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(preflightText.contains("Entry Preflight Passed"));
    assertTrue(preflightText.contains("Event class"));
    assertTrue(preflightText.contains("SETTLED_SALE"));
    assertTrue(preflightText.contains("Journal lines"));
    assertTrue(preflightText.contains("2100"));
    outputStream.reset();
    responseWriter.writePostEntryResult(
        CliPostEntryResultFixtures.committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-17"),
            Instant.parse("2026-04-17T10:15:31Z"),
            false),
        OutputMode.TEXT);
    String committedText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(committedText.contains("Entry Committed"));
    assertTrue(committedText.contains("SETTLED_SALE"));
    assertTrue(committedText.contains("Journal lines"));
    assertTrue(committedText.contains("12.10"));
    assertFalse(committedText.contains("Contained typed events"));
  }

  @Test
  void writeDeclareAccountResult_coversAllSuccessOutcomesAcrossOutputModes() {
    DeclaredAccount account =
        CliIoFixtureSupport.declaredAccount(
            "1100",
            "Operating Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-17T10:20:30Z"));

    ByteArrayOutputStream reactivatedJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter reactivatedJsonWriter =
        new CliResponseWriter(utf8PrintStream(reactivatedJsonOutput));
    reactivatedJsonWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Reactivated(account), OutputMode.JSON);
    assertJsonContains(reactivatedJsonOutput, "\"status\":\"ok\"");
    assertJsonContains(reactivatedJsonOutput, "\"outcome\":\"reactivated\"");
    assertJsonContains(reactivatedJsonOutput, "\"accountCode\":\"1100\"");

    ByteArrayOutputStream renamedTextOutput = new ByteArrayOutputStream();
    CliResponseWriter renamedTextWriter = new CliResponseWriter(utf8PrintStream(renamedTextOutput));
    renamedTextWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Renamed(account), OutputMode.TEXT);
    String renamedText = renamedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(renamedText.contains("Account Renamed"));
    assertTrue(renamedText.contains("Outcome"));
    assertTrue(renamedText.contains("renamed"));

    ByteArrayOutputStream reactivatedTextOutput = new ByteArrayOutputStream();
    CliResponseWriter reactivatedTextWriter =
        new CliResponseWriter(utf8PrintStream(reactivatedTextOutput));
    reactivatedTextWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Reactivated(account), OutputMode.TEXT);
    String reactivatedText = reactivatedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(reactivatedText.contains("Account Reactivated"));
    assertTrue(reactivatedText.contains("reactivated"));

    ByteArrayOutputStream renamedJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter renamedJsonWriter = new CliResponseWriter(utf8PrintStream(renamedJsonOutput));
    renamedJsonWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Renamed(account), OutputMode.JSON);
    assertJsonContains(renamedJsonOutput, "\"outcome\":\"renamed\"");
    assertJsonContains(renamedJsonOutput, "\"accountName\":\"Operating Cash\"");

    ByteArrayOutputStream unchangedJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter unchangedJsonWriter =
        new CliResponseWriter(utf8PrintStream(unchangedJsonOutput));
    unchangedJsonWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Unchanged(account), OutputMode.JSON);
    assertJsonContains(unchangedJsonOutput, "\"outcome\":\"unchanged\"");
    assertJsonContains(unchangedJsonOutput, "\"declaredAt\":\"2026-04-17T10:20:30Z\"");

    ByteArrayOutputStream unchangedTextOutput = new ByteArrayOutputStream();
    CliResponseWriter unchangedTextWriter =
        new CliResponseWriter(utf8PrintStream(unchangedTextOutput));
    unchangedTextWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Unchanged(account), OutputMode.TEXT);
    String unchangedText = unchangedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(unchangedText.contains("Account Unchanged"));
    assertTrue(unchangedText.contains("unchanged"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareAccountResult(
                    new DeclareAccountResult.Reactivated(account), OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareAccountResult(
                    new DeclareAccountResult.Renamed(account), OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareAccountResult(
                    new DeclareAccountResult.Unchanged(account), OutputMode.CSV));

    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new DeclareAccountResult.Reactivated(account)));
    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new DeclareAccountResult.Renamed(account)));
    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new DeclareAccountResult.Unchanged(account)));
  }

  @Test
  void writeInterimResultSweepResult_rendersExplicitEmptyClosingPostingSet() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Swept(
            new dev.erst.fingrind.contract.bookkeeping.SweptInterimResult(
                1,
                new dev.erst.fingrind.core.ReportingPeriod(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                new AccountCode("3200"),
                List.of(),
                Instant.parse("2026-04-30T12:00:00Z"),
                List.of())),
        OutputMode.TEXT);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Generated interim-result-sweep postings"));
    assertTrue(text.contains("(none)"));
    assertTrue(text.contains("No closing movements were required"));
  }

  @Test
  void writeInterimResultSweepResult_omitsEmptyOutcomeWhenClosingMovementsExist() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Swept(CliFixtureSupport.sampleSweptInterimResult()),
        OutputMode.TEXT);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Generated interim-result-sweep postings"));
    assertFalse(text.contains("No closing movements were required"));
  }

  @Test
  void writeInterimResultSweepResult_omitsEmptyOutcomeWhenPostingIdsExistWithoutClosedTotals() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Swept(
            new dev.erst.fingrind.contract.bookkeeping.SweptInterimResult(
                1,
                new dev.erst.fingrind.core.ReportingPeriod(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                new AccountCode("3200"),
                List.of(),
                Instant.parse("2026-04-30T12:00:00Z"),
                List.of(new PostingId("posting-close-1")))),
        OutputMode.TEXT);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Generated interim-result-sweep postings"));
    assertFalse(text.contains("No closing movements were required"));
  }

  @Test
  void writeFiscalYearCloseResult_rendersExplicitEmptyClosingPostingSet() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Closed(
            new dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear(
                1,
                new dev.erst.fingrind.core.ReportingPeriod(
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
                new AccountCode("3000"),
                new AccountCode("3200"),
                new AccountCode("3300"),
                Instant.parse("2026-12-31T12:00:00Z"),
                List.of()),
            false),
        OutputMode.TEXT);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Generated fiscal-year-close postings"));
    assertTrue(text.contains("(none)"));
  }

  @Test
  void writeAdministrativeAndWriteSuccesses_rejectCsvOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeGenerateBookKeyFileResult(
                new dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile(
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
                Path.of("keys/rotated.key"),
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
            responseWriter.writeInterimResultSweepResult(
                new InterimResultSweepResult.Swept(CliFixtureSupport.sampleSweptInterimResult()),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeFiscalYearCloseResult(
                new FiscalYearCloseResult.Closed(CliFixtureSupport.sampleClosedFiscalYear(), false),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                CliPostEntryResultFixtures.preflightAccepted(
                    new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                CliPostEntryResultFixtures.committed(
                    new PostingId("posting-1"),
                    new IdempotencyKey("idem-1"),
                    LocalDate.parse("2026-04-17"),
                    Instant.parse("2026-04-17T10:15:31Z"),
                    false),
                OutputMode.CSV));
  }

  @Test
  void writeAdministrativeAndWriteRejections_renderTextDiagnosticsWhenTextModeIsSelected()
      throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
        OutputMode.TEXT);
    String rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("book-already-initialized"), rendered);
    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.IdempotencyKeyConflict()),
        OutputMode.TEXT);
    rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("idempotency-key-conflict"), rendered);
    assertTrue(rendered.contains("idem-1"), rendered);
    outputStream.reset();
    responseWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Rejected(
            new CloseTargetAccountCandidateMissing(
                dev.erst.fingrind.core.FinancialPositionLineClassification.RESULT_HOLDING,
                List.of())),
        OutputMode.TEXT);
    rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("close-target-account-candidate-missing"), rendered);
    outputStream.reset();
    responseWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Rejected(
            new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))),
        OutputMode.TEXT);
    rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("fiscal-year-close-must-end-at"), rendered);
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
  void writeRekeyBookResult_writesSuccessAndRejectionEnvelopes() throws Exception {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        Path.of("keys").resolve("rotated.key"));
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(successJson, "\"status\":\"ok\"");
    assertJsonContains(successJson, "\"bookFile\"");
    assertJsonContains(successJson, "\"newBookKeyFile\"");
    var successEnvelope = readJson(successOutput);
    assertTrue(successEnvelope.path("payload").path("replacementBookKeyFile").isMissingNode());
    assertEquals(
        ProtocolArtifactOutput.bookKeyFileFormat(),
        successEnvelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        Path.of("keys/rotated.key").toAbsolutePath().normalize().toString().replace('\\', '/'),
        successEnvelope.path("artifacts").get(0).path("path").asText().replace('\\', '/'));
    ByteArrayOutputStream rejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter rejectionWriter = new CliResponseWriter(utf8PrintStream(rejectionOutput));
    rejectionWriter.writeRekeyBookResult(
        new RekeyBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .SecretTargetOccupied(Path.of("keys/occupied.key"))),
        Path.of("keys/occupied.key"));
    String rejectionJson = rejectionOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(rejectionJson, "\"status\":\"rejected\"");
    assertJsonContains(rejectionJson, "\"code\":\"secret-target-occupied\"");
  }

  @Test
  void writeRekeyBookResult_supportsNonFileReplacementSecretSources() throws Exception {
    ByteArrayOutputStream standardInputTextOutput = new ByteArrayOutputStream();
    CliResponseWriter standardInputTextWriter =
        new CliResponseWriter(utf8PrintStream(standardInputTextOutput));
    standardInputTextWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        Path.of("keys/rotated.key"),
        OutputMode.TEXT);
    String standardInputText = standardInputTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(standardInputText.contains("New book key file"));

    ByteArrayOutputStream interactivePromptTextOutput = new ByteArrayOutputStream();
    CliResponseWriter interactivePromptTextWriter =
        new CliResponseWriter(utf8PrintStream(interactivePromptTextOutput));
    interactivePromptTextWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        Path.of("keys/rotated.key"),
        OutputMode.TEXT);
    String interactivePromptText = interactivePromptTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(interactivePromptText.contains("New book key file"));

    ByteArrayOutputStream standardInputJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter standardInputJsonWriter =
        new CliResponseWriter(utf8PrintStream(standardInputJsonOutput));
    standardInputJsonWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        Path.of("keys/rotated.key"));
    String standardInputJson = standardInputJsonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(standardInputJson, "\"newBookKeyFile\"");
    assertFalse(readJson(standardInputJsonOutput).path("artifacts").isMissingNode());

    ByteArrayOutputStream interactivePromptJsonOutput = new ByteArrayOutputStream();
    CliResponseWriter interactivePromptJsonWriter =
        new CliResponseWriter(utf8PrintStream(interactivePromptJsonOutput));
    interactivePromptJsonWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")),
        Path.of("keys/rotated.key"));
    String interactivePromptJson = interactivePromptJsonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(interactivePromptJson, "\"newBookKeyFile\"");
    assertFalse(readJson(interactivePromptJsonOutput).path("artifacts").isMissingNode());
  }

  @Test
  void writeMaintenanceResults_supportSuccessEnvelopesAndTextOutput() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("backup/entity.sqlite")),
            hint(Path.of("backup/entity.key"))),
        OutputMode.TEXT);
    String backupText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(backupText.contains("Book Backed Up"));
    assertTrue(backupText.contains("Backup file"));
    assertTrue(backupText.contains("entity.sqlite"));
    outputStream.reset();

    responseWriter.writeRestoreBookResult(
        new RestoreBookResult.Restored(
            hint(Path.of("books/entity.sqlite")), hint(Path.of("books/entity.book-key"))),
        OutputMode.TEXT);
    String restoreText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(restoreText.contains("Book Restored"));
    assertTrue(restoreText.contains("Book key file"));
    outputStream.reset();

    responseWriter.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(
            hint(Path.of("books/entity.sqlite")),
            List.of(
                hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                hint(Path.of("books/entity.rekey-rollback-b.sqlite")))),
        OutputMode.TEXT);
    String inspectText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(inspectText.contains("Rekey Rollback Artifacts"));
    assertTrue(inspectText.contains("rollback-a"));
    assertTrue(inspectText.contains("rollback-b"));
    outputStream.reset();

    responseWriter.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.JSON);
    var restoreJson = readJson(outputStream);
    assertEquals("ok", restoreJson.path("status").stringValue());
    assertTrue(restoreJson.path("payload").path("rollbackArtifact").isMissingNode());
    assertEquals(
        ProtocolArtifactOutput.rollbackBookFileFormat(),
        restoreJson.path("artifacts").get(0).path("format").stringValue());
  }

  @Test
  void writeMaintenanceRejections_renderTextDiagnosticsWhenTextModeIsSelected() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBackupBookResult(
        new BackupBookResult.Rejected(
            new BookMaintenanceRejection.BookHasBlockingArtifacts(
                hint(Path.of("books/entity.sqlite")),
                List.of(
                    hint(Path.of("books/entity.sqlite-wal")),
                    hint(Path.of("books/entity.sqlite-shm"))))),
        OutputMode.TEXT);
    String rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("book-has-blocking-artifacts"), rendered);
    assertTrue(rendered.contains("Blocking artifacts"), rendered);
    assertTrue(rendered.contains("entity.sqlite-wal"), rendered);
    assertTrue(rendered.contains("entity.sqlite-shm"), rendered);
    outputStream.reset();

    responseWriter.writeInspectRekeyRollbackResult(
        new RekeyRollbackResult.Rejected(
            new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
                hint(Path.of("books/entity.sqlite")),
                List.of(
                    hint(Path.of("books/entity.rekey-rollback-a.sqlite")),
                    hint(Path.of("books/entity.rekey-rollback-b.sqlite"))))),
        OutputMode.JSON);
    var json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("rollback-artifact-selection-required", json.path("code").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(Path.of("books/entity.sqlite")),
        json.path("details").path("bookFile").asText().replace('\\', '/'));
    assertEquals(2, json.path("details").path("rollbackArtifacts").size());
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

    var envelope = readJson(outputStream);
    assertEquals("ok", envelope.path("status").stringValue());
    assertTrue(envelope.path("payload").path("rollbackArtifact").isMissingNode());
    assertEquals(
        ProtocolArtifactOutput.rollbackBookFileFormat(),
        envelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(Path.of("books/entity.rekey-rollback.sqlite")),
        envelope.path("artifacts").get(0).path("path").asText().replace('\\', '/'));
  }

  @Test
  void writeRekeyRollbackRestoreAndDelete_supportTextAndRejectCsv() {
    ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
    CliResponseWriter restoreWriter = new CliResponseWriter(utf8PrintStream(restoreOutput));
    restoreWriter.writeRestoreRekeyRollbackResult(
        new RekeyRollbackResult.Restored(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.TEXT);
    assertTrue(
        restoreOutput.toString(StandardCharsets.UTF_8).contains("Book Restored From Rollback"));

    ByteArrayOutputStream deleteOutput = new ByteArrayOutputStream();
    CliResponseWriter deleteWriter = new CliResponseWriter(utf8PrintStream(deleteOutput));
    deleteWriter.writeDeleteRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(
            hint(Path.of("books/entity.sqlite")),
            hint(Path.of("books/entity.rekey-rollback.sqlite"))),
        OutputMode.TEXT);
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
  void writeRekeyRollbackRestoreAndDelete_emitMaintenanceJsonRejectionsAcrossOutputModes()
      throws Exception {
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
        OutputMode.TEXT);

    String rendered = deleteOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Rejected"), rendered);
    assertTrue(rendered.contains("rollback-artifact-selection-required"), rendered);
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
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("1000"),
                dev.erst.fingrind.core.AccountType.ASSET,
                dev.erst.fingrind.core.AccountType.REVENUE)));
    ByteArrayOutputStream listRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listRejectionOutput));
    listRejectionWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()));
    assertJsonContains(declareRejectionOutput, "\"code\":\"administration-book-not-initialized\"");
    String declareConflictJson = declareConflictOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(declareConflictJson, "\"code\":\"account-type-conflict\"");
    assertJsonContains(declareConflictJson, "\"accountCode\":\"1000\"");
    assertJsonContains(declareConflictJson, "\"existingAccountType\":\"ASSET\"");
    assertJsonContains(declareConflictJson, "\"requestedAccountType\":\"REVENUE\"");
    assertTrue(declareConflictJson.contains("already exists with account type"));
    assertJsonContains(listRejectionOutput, "\"code\":\"query-book-not-initialized\"");
  }

  @Test
  void writeInterimResultSweepResult_writesSuccessAndDetailedRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Swept(CliFixtureSupport.sampleSweptInterimResult()),
        OutputMode.JSON);
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(successJson, "\"status\":\"ok\"");
    assertJsonContains(successJson, "\"sweepOrder\":1");
    assertJsonContains(successJson, "\"sweepPostingIds\":[\"posting-close-1\"]");

    ByteArrayOutputStream missingOutput = new ByteArrayOutputStream();
    CliResponseWriter missingWriter = new CliResponseWriter(utf8PrintStream(missingOutput));
    missingWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Rejected(
            new CloseTargetAccountCandidateMissing(
                dev.erst.fingrind.core.FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200")))),
        OutputMode.JSON);
    assertJsonContains(missingOutput, "\"code\":\"close-target-account-candidate-missing\"");
    assertJsonContains(
        missingOutput, "\"requiredFinancialPositionLineClassification\":\"RESULT_HOLDING\"");

    ByteArrayOutputStream horizonOutput = new ByteArrayOutputStream();
    CliResponseWriter horizonWriter = new CliResponseWriter(utf8PrintStream(horizonOutput));
    horizonWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Rejected(
            new BookAdministrationRejection.InterimResultSweepMustStartAt(
                LocalDate.parse("2026-04-01"))),
        OutputMode.JSON);
    String horizonJson = horizonOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(horizonJson, "\"code\":\"interim-result-sweep-must-start-at\"");
    assertJsonContains(horizonJson, "\"requiredEffectiveDateFrom\":\"2026-04-01\"");

    ByteArrayOutputStream typeConflictOutput = new ByteArrayOutputStream();
    CliResponseWriter typeConflictWriter =
        new CliResponseWriter(utf8PrintStream(typeConflictOutput));
    typeConflictWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Rejected(
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
    ambiguousWriter.writeInterimResultSweepResult(
        new InterimResultSweepResult.Rejected(
            new CloseTargetAccountCandidateAmbiguous(
                dev.erst.fingrind.core.FinancialPositionLineClassification.OTHER_EQUITY,
                List.of(new AccountCode("3200"), new AccountCode("3210")))),
        OutputMode.JSON);
    assertJsonContains(ambiguousOutput, "\"code\":\"close-target-account-candidate-ambiguous\"");
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new InterimResultSweepResult.Rejected(
                new CloseTargetAccountCandidateMissing(
                    dev.erst.fingrind.core.FinancialPositionLineClassification.RESULT_HOLDING,
                    List.of()))));
  }

  @Test
  void writeFiscalYearCloseResult_writesSuccessAndDetailedRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Closed(CliFixtureSupport.sampleClosedFiscalYear(), false),
        OutputMode.JSON);
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(successJson, "\"status\":\"ok\"");
    assertJsonContains(successJson, "\"closeOrder\":1");
    assertJsonContains(successJson, "\"capitalAccountCode\":\"3000\"");
    assertJsonContains(successJson, "\"resultHoldingAccountCode\":\"3200\"");
    assertJsonContains(successJson, "\"retainedAccumulatedAccountCode\":\"3300\"");
    assertJsonContains(
        successJson, "\"closePostingIds\":[\"posting-close-1\",\"posting-close-2\"]");

    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    CliResponseWriter rejectedWriter = new CliResponseWriter(utf8PrintStream(rejectedOutput));
    rejectedWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Rejected(
            new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))),
        OutputMode.JSON);
    String rejectedJson = rejectedOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(rejectedJson, "\"code\":\"fiscal-year-close-must-start-at\"");
    assertJsonContains(rejectedJson, "\"requiredEffectiveDateFrom\":\"2026-01-01\"");

    ByteArrayOutputStream futureDateOutput = new ByteArrayOutputStream();
    CliResponseWriter futureDateWriter = new CliResponseWriter(utf8PrintStream(futureDateOutput));
    futureDateWriter.writeFiscalYearCloseResult(
        new FiscalYearCloseResult.Rejected(
            new BookAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))),
        OutputMode.JSON);
    assertJsonContains(futureDateOutput, "\"code\":\"fiscal-year-close-future-date\"");
    assertJsonContains(futureDateOutput, "\"attemptedEffectiveDateTo\":\"2027-01-01\"");

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Closed(CliFixtureSupport.sampleClosedFiscalYear(), false)));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Rejected(
                new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                    LocalDate.parse("2026-12-31")))));
  }

  @Test
  void writePostingRejections_includesStructuredCloseAndReversalDetails() {
    String transferredPeriodResultJson =
        rejectedJson(
            new PostingRejection.SweptInterimResultViolation(
                LocalDate.parse("2026-04-30"), LocalDate.parse("2026-05-01")));
    assertJsonContains(
        transferredPeriodResultJson, "\"transferredThroughEffectiveDate\":\"2026-04-30\"");
    assertJsonContains(transferredPeriodResultJson, "\"attemptedEffectiveDate\":\"2026-05-01\"");

    String resultHoldingJson =
        rejectedJson(
            new PostingRejection.ReservedResultClassification(
                new AccountCode("3200"),
                dev.erst.fingrind.core.FinancialPositionLineClassification.RESULT_HOLDING));
    assertJsonContains(resultHoldingJson, "\"accountCode\":\"3200\"");

    String missingPriorPostingJson =
        rejectedJson(new PostingRejection.ReversalTargetNotFound(new PostingId("posting-9")));
    assertJsonContains(missingPriorPostingJson, "\"priorPostingId\":\"posting-9\"");

    String reversalTargetIsReversalJson =
        rejectedJson(
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("posting-8a")));
    assertJsonContains(reversalTargetIsReversalJson, "\"priorPostingId\":\"posting-8a\"");

    String existingReversalJson =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-8")));
    assertJsonContains(existingReversalJson, "\"priorPostingId\":\"posting-8\"");

    String negateTargetJson =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-7")));
    assertJsonContains(negateTargetJson, "\"priorPostingId\":\"posting-7\"");
  }
}
