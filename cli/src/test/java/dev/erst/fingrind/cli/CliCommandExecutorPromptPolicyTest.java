package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

/** Covers machine-output prompt refusal branches across every prompt-capable CLI executor. */
class CliCommandExecutorPromptPolicyTest extends CliResponseWriterTestSupport {
  private static final Path BOOK_FILE = Path.of("books/entity.sqlite");
  private static final Path REQUEST_FILE = Path.of("requests/request.json");
  private static final Path BACKUP_FILE = Path.of("backup/entity.backup.sqlite");
  private static final Path BACKUP_KEY_FILE = Path.of("backup/entity.backup.key");
  private static final BookAccess PROMPT_BOOK_ACCESS =
      new BookAccess(
          BOOK_FILE, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE, java.util.List.of());

  @Test
  void administrativeExecutors_rejectInteractivePromptForJsonOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliExplodingWorkflow workflow =
        new CliExplodingWorkflow(new IllegalStateException("workflow should not run"));
    CliAdministrativeCommandExecutor executor =
        new CliAdministrativeCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            failureWriter(outputStream),
            workflow,
            workflow);

    assertPromptFailure(
        outputStream,
        () -> executor.runOpenBookCommand(PROMPT_BOOK_ACCESS, openBookCommand(), OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runRekeyBookCommand(
                PROMPT_BOOK_ACCESS, Path.of("keys/entity.new.book-key"), OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runBackupBookCommand(
                PROMPT_BOOK_ACCESS,
                BACKUP_FILE,
                BACKUP_KEY_FILE,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runAttestationRegistryMutationCommand(
                OperationId.ENROLL_KEY, PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () -> executor.runDeclareAccountCommand(PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runDeclareTaxRegistrationCommand(
                PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runInterimResultSweepCommand(
                PROMPT_BOOK_ACCESS, LocalDate.parse("2026-04-30"), OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () -> executor.runFiscalYearCloseCommand(PROMPT_BOOK_ACCESS, 2026, OutputMode.JSON));
  }

  @Test
  void queryExecutors_rejectInteractivePromptForJsonOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliExplodingWorkflow workflow =
        new CliExplodingWorkflow(new IllegalStateException("workflow should not run"));
    CliQueryCommandExecutor executor =
        new CliQueryCommandExecutor(
            bookReadWriter(outputStream), failureWriter(outputStream), workflow);

    assertPromptFailure(
        outputStream, () -> executor.runInspectBookCommand(PROMPT_BOOK_ACCESS, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runListAccountsCommand(
                PROMPT_BOOK_ACCESS,
                new ListAccountsQuery(10, Optional.empty()),
                false,
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runGetPostingCommand(
                PROMPT_BOOK_ACCESS,
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                false,
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runListPostingsCommand(
                PROMPT_BOOK_ACCESS,
                new ListPostingsQuery(Optional.empty(), null, null, 10, Optional.empty()),
                false,
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runListTaxRegistrationsCommand(
                PROMPT_BOOK_ACCESS,
                new ListTaxRegistrationsQuery(10, Optional.empty()),
                false,
                OutputMode.JSON));
  }

  @Test
  void mutationExecutors_rejectInteractivePromptForMachineOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliExplodingWorkflow workflow =
        new CliExplodingWorkflow(new IllegalStateException("workflow should not run"));
    CliMutationCommandExecutor executor =
        new CliMutationCommandExecutor(
            new CliRequestReader(new ByteArrayInputStream(new byte[0])),
            mutationWriter(outputStream),
            planWriter(outputStream),
            failureWriter(outputStream),
            workflow);

    assertPromptFailure(
        outputStream,
        () -> executor.runPreflightEntryCommand(PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () -> executor.runPostEntryCommand(PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runExecutePlanCommand(
                PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON, PlanResultDetail.FULL));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runRecordEntryCommand(
                PROMPT_BOOK_ACCESS,
                REQUEST_FILE,
                OutputMode.JSON,
                OperationId.RECORD_SALE_SETTLED));
  }

  @Test
  void reportExecutors_rejectInteractivePromptForMachineOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliExplodingWorkflow workflow =
        new CliExplodingWorkflow(new IllegalStateException("workflow should not run"));
    CliReportCommandExecutor executor =
        new CliReportCommandExecutor(
            reportWriter(outputStream),
            failureWriter(outputStream),
            workflow,
            new CliPdfReportExporter(new PdfReportService("FinGrind", "0.57.0", fixedClock())),
            fixedClock());
    CliReportOutput jsonOutput = new CliReportOutput(OutputMode.JSON, null);

    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                AccountBalanceQuery.unbounded(new AccountCode("1000")),
                jsonOutput,
                executor.handlers().accountBalance()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new TrialBalanceQuery(
                    Optional.of(LocalDate.parse("2026-04-30")),
                    dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
                    ComparativeSelection.none()),
                jsonOutput,
                executor.handlers().trialBalance()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new AccountLedgerQuery(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
                    dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
                    50,
                    java.util.Optional.empty()),
                jsonOutput,
                executor.handlers().accountLedger()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new PeriodSummaryQuery(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                jsonOutput,
                executor.handlers().periodSummary()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new FinancialPositionQuery(
                    Optional.of(LocalDate.parse("2026-04-30")), ComparativeSelection.none()),
                jsonOutput,
                executor.handlers().financialPosition()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new IncomeStatementQuery(
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    ComparativeSelection.none()),
                jsonOutput,
                executor.handlers().incomeStatement()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new ChangesInEquityQuery(
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30"),
                    ComparativeSelection.none()),
                jsonOutput,
                executor.handlers().changesInEquity()));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runConfiguredReportCommand(
                PROMPT_BOOK_ACCESS,
                new TaxObligationQuery(
                    new TaxRegistrationId("vat-lv"),
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-04-30")),
                jsonOutput,
                executor.handlers().taxObligation()));
    assertEquals("", diagnosticsStream.toString(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void assertPromptFailure(ByteArrayOutputStream outputStream, IntSupplier invocation)
      throws IOException {
    int exitCode = invocation.getAsInt();
    assertEquals(2, exitCode);
    tools.jackson.databind.JsonNode envelope = readJson(outputStream);
    assertEquals("error", envelope.path("status").stringValue());
    assertEquals("unsupported-output-selection", envelope.path("code").stringValue());
    assertEquals(
        "Interactive passphrase prompting is only supported with --output text.",
        envelope.path("message").stringValue());
    assertTrue(envelope.path("hint").stringValue().contains("--output text"));
    assertEquals("--output", envelope.path("argument").stringValue());
    outputStream.reset();
  }
}
