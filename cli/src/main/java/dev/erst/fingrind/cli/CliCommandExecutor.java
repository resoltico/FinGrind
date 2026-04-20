package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes parsed non-discovery CLI commands against the configured FinGrind workflow. */
final class CliCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

  CliCommandExecutor(
      CliRequestReader requestReader,
      CliResponseWriter responseWriter,
      CliBookWorkflow bookWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    responseWriter.writeGenerateBookKeyFileResult(
        SqliteBookKeyFileGenerator.generate(bookKeyFilePath), outputMode);
    return 0;
  }

  int runOpenBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    OpenBookResult result = bookWorkflow.openBook(bookAccess);
    responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runRekeyBookCommand(
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    RekeyBookResult result = bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource);
    responseWriter.writeRekeyBookResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    DeclareAccountResult result = bookWorkflow.declareAccount(bookAccess, command);
    responseWriter.writeDeclareAccountResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    BookInspection inspection = bookWorkflow.inspectBook(bookAccess);
    responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode);
    return 0;
  }

  int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    ListAccountsResult result = bookWorkflow.listAccounts(bookAccess, query);
    responseWriter.writeListAccountsResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runGetPostingCommand(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId, OutputMode outputMode) {
    GetPostingResult result = bookWorkflow.getPosting(bookAccess, postingId);
    responseWriter.writeGetPostingResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    ListPostingsResult result = bookWorkflow.listPostings(bookAccess, query);
    responseWriter.writeListPostingsResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    AccountBalanceResult result = bookWorkflow.accountBalance(bookAccess, query);
    exportAccountBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeAccountBalanceResult(result, output.outputMode());
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    TrialBalanceResult result = bookWorkflow.trialBalance(bookAccess, query);
    exportTrialBalanceIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeTrialBalanceResult(result, output.outputMode());
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    AccountLedgerResult result = bookWorkflow.accountLedger(bookAccess, query);
    exportAccountLedgerIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writeAccountLedgerResult(result, output.outputMode());
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    PeriodSummaryResult result = bookWorkflow.periodSummary(bookAccess, query);
    exportPeriodSummaryIfRequested(bookAccess.bookFilePath(), result, output.pdfOutPath());
    responseWriter.writePeriodSummaryResult(result, output.outputMode());
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runExecutePlanCommand(BookAccess bookAccess, Path requestFile) {
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    LedgerPlanResult result = bookWorkflow.executePlan(bookAccess, plan);
    responseWriter.writeLedgerPlanResult(result);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runPreflightEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    PreflightEntryResult result = bookWorkflow.preflight(bookAccess, command);
    responseWriter.writePostEntryResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  int runPostEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    CommitEntryResult result = bookWorkflow.commit(bookAccess, command);
    responseWriter.writePostEntryResult(result, outputMode);
    return CliExecutionSupport.exitCodeFor(result);
  }

  private void exportAccountBalanceIfRequested(
      Path bookFilePath, AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportAccountBalance(outputPath, bookFilePath, reported.snapshot());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportTrialBalanceIfRequested(
      Path bookFilePath, TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportTrialBalance(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportAccountLedgerIfRequested(
      Path bookFilePath, AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportAccountLedger(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }

  private void exportPeriodSummaryIfRequested(
      Path bookFilePath, PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    result.fold(
        reported -> {
          pdfReportExporter.exportPeriodSummary(outputPath, bookFilePath, reported.report());
          return Boolean.TRUE;
        },
        rejected -> Boolean.FALSE);
  }
}
