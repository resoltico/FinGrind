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
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
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
      new BookAccess(BOOK_FILE, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE);

  @Test
  void administrativeExecutors_rejectInteractivePromptForJsonOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliWorkflowDoubleSupport.ExplodingWorkflow workflow =
        new CliWorkflowDoubleSupport.ExplodingWorkflow(
            new IllegalStateException("workflow should not run"));
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
                PROMPT_BOOK_ACCESS,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runBackupBookCommand(
                PROMPT_BOOK_ACCESS, BACKUP_FILE, BACKUP_KEY_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runRestoreRekeyRollbackCommand(
                BOOK_FILE,
                null,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () -> executor.runDeleteRekeyRollbackCommand(PROMPT_BOOK_ACCESS, null, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () -> executor.runDeclareAccountCommand(PROMPT_BOOK_ACCESS, REQUEST_FILE, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runPeriodResultTransferCommand(
                PROMPT_BOOK_ACCESS,
                new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                OutputMode.JSON));
  }

  @Test
  void queryExecutors_rejectInteractivePromptForJsonOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliWorkflowDoubleSupport.ExplodingWorkflow workflow =
        new CliWorkflowDoubleSupport.ExplodingWorkflow(
            new IllegalStateException("workflow should not run"));
    CliQueryCommandExecutor executor =
        new CliQueryCommandExecutor(
            bookReadWriter(outputStream), failureWriter(outputStream), workflow);

    assertPromptFailure(
        outputStream, () -> executor.runInspectBookCommand(PROMPT_BOOK_ACCESS, OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runListAccountsCommand(
                PROMPT_BOOK_ACCESS, new ListAccountsQuery(10, Optional.empty()), OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runGetPostingCommand(
                PROMPT_BOOK_ACCESS, new PostingId("posting-1"), OutputMode.JSON));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runListPostingsCommand(
                PROMPT_BOOK_ACCESS,
                new ListPostingsQuery(Optional.empty(), null, null, 10, Optional.empty()),
                OutputMode.JSON));
  }

  @Test
  void mutationExecutors_rejectInteractivePromptForMachineOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliWorkflowDoubleSupport.ExplodingWorkflow workflow =
        new CliWorkflowDoubleSupport.ExplodingWorkflow(
            new IllegalStateException("workflow should not run"));
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
                PROMPT_BOOK_ACCESS, REQUEST_FILE, PlanResultDetail.FULL));
  }

  @Test
  void reportExecutors_rejectInteractivePromptForMachineOutput() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliWorkflowDoubleSupport.ExplodingWorkflow workflow =
        new CliWorkflowDoubleSupport.ExplodingWorkflow(
            new IllegalStateException("workflow should not run"));
    CliReportCommandExecutor executor =
        new CliReportCommandExecutor(
            reportWriter(outputStream),
            failureWriter(outputStream),
            new CliDiagnosticsWriter(utf8PrintStream(diagnosticsStream)),
            workflow,
            new CliPdfReportExporter(new PdfReportService("FinGrind", "0.54.0", fixedClock())));
    CliCommand.ReportOutput jsonOutput = new CliCommand.ReportOutput(OutputMode.JSON, null);

    assertPromptFailure(
        outputStream,
        () ->
            executor.runAccountBalanceCommand(
                PROMPT_BOOK_ACCESS,
                AccountBalanceQuery.unbounded(new AccountCode("1000")),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runTrialBalanceCommand(
                PROMPT_BOOK_ACCESS,
                new TrialBalanceQuery(
                    Optional.of(LocalDate.parse("2026-04-30")),
                    dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runAccountLedgerCommand(
                PROMPT_BOOK_ACCESS,
                AccountLedgerQuery.unbounded(new AccountCode("1000")),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runPeriodSummaryCommand(
                PROMPT_BOOK_ACCESS,
                new PeriodSummaryQuery(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runFinancialPositionCommand(
                PROMPT_BOOK_ACCESS,
                new FinancialPositionQuery(Optional.of(LocalDate.parse("2026-04-30"))),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runIncomeStatementCommand(
                PROMPT_BOOK_ACCESS,
                new IncomeStatementQuery(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                jsonOutput));
    assertPromptFailure(
        outputStream,
        () ->
            executor.runChangesInEquityCommand(
                PROMPT_BOOK_ACCESS,
                new ChangesInEquityQuery(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                jsonOutput));
    assertEquals("", diagnosticsStream.toString(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void assertPromptFailure(ByteArrayOutputStream outputStream, IntSupplier invocation)
      throws IOException {
    int exitCode = invocation.getAsInt();
    assertEquals(2, exitCode);
    tools.jackson.databind.JsonNode envelope = readJson(outputStream);
    assertEquals("error", envelope.path("status").textValue());
    assertEquals("unsupported-output-selection", envelope.path("code").textValue());
    assertEquals(
        "Interactive passphrase prompting is only supported with --output text.",
        envelope.path("message").textValue());
    assertTrue(envelope.path("hint").textValue().contains("--output text"));
    assertEquals("--output", envelope.path("argument").textValue());
    outputStream.reset();
  }
}
