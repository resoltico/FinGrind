package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes reporting CLI commands and exports optional PDF artifacts. */
final class CliReportCommandExecutor {
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

  CliReportCommandExecutor(
      CliResponseWriter responseWriter,
      CliBookWorkflow bookWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.accountBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          exportAccountBalance(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeAccountBalanceResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.trialBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          exportTrialBalance(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeTrialBalanceResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.accountLedger(bookAccess, query),
        output.outputMode(),
        result -> {
          exportAccountLedger(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writeAccountLedgerResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.periodSummary(bookAccess, query),
        output.outputMode(),
        result -> {
          exportPeriodSummary(bookAccess.bookFilePath(), result, output.pdfOutPath());
          responseWriter.writePeriodSummaryResult(result, output.outputMode());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  private void exportAccountBalance(
      Path bookFilePath, AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountBalanceResult.Reported reported ->
          pdfReportExporter.exportAccountBalance(outputPath, bookFilePath, reported.snapshot());
      case AccountBalanceResult.Rejected _ -> {}
    }
  }

  private void exportTrialBalance(
      Path bookFilePath, TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case TrialBalanceResult.Reported reported ->
          pdfReportExporter.exportTrialBalance(outputPath, bookFilePath, reported.report());
      case TrialBalanceResult.Rejected _ -> {}
    }
  }

  private void exportAccountLedger(
      Path bookFilePath, AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountLedgerResult.Reported reported ->
          pdfReportExporter.exportAccountLedger(outputPath, bookFilePath, reported.report());
      case AccountLedgerResult.Rejected _ -> {}
    }
  }

  private void exportPeriodSummary(
      Path bookFilePath, PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case PeriodSummaryResult.Reported reported ->
          pdfReportExporter.exportPeriodSummary(outputPath, bookFilePath, reported.report());
      case PeriodSummaryResult.Rejected _ -> {}
    }
  }
}
