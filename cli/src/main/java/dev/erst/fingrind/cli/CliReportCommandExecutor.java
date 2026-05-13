package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes reporting CLI commands and exports optional PDF artifacts. */
final class CliReportCommandExecutor {
  private final CliResponseWriter responseWriter;
  private final CliDiagnosticsWriter diagnosticsWriter;
  private final CliBookWorkflow bookWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

  CliReportCommandExecutor(
      CliResponseWriter responseWriter,
      CliDiagnosticsWriter diagnosticsWriter,
      CliBookWorkflow bookWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.diagnosticsWriter = Objects.requireNonNull(diagnosticsWriter, "diagnosticsWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.accountBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          responseWriter.writeAccountBalanceResult(result, output.outputMode());
          exportAccountBalance(result, output.pdfOutPath());
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
          responseWriter.writeTrialBalanceResult(result, output.outputMode());
          exportTrialBalance(result, output.pdfOutPath());
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
          responseWriter.writeAccountLedgerResult(result, output.outputMode());
          exportAccountLedger(result, output.pdfOutPath());
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
          responseWriter.writePeriodSummaryResult(result, output.outputMode());
          exportPeriodSummary(result, output.pdfOutPath());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runFinancialPositionCommand(
      BookAccess bookAccess, FinancialPositionQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.financialPosition(bookAccess, query),
        output.outputMode(),
        result -> {
          responseWriter.writeFinancialPositionResult(result, output.outputMode());
          exportFinancialPosition(result, output.pdfOutPath());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runIncomeStatementCommand(
      BookAccess bookAccess, IncomeStatementQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.incomeStatement(bookAccess, query),
        output.outputMode(),
        result -> {
          responseWriter.writeIncomeStatementResult(result, output.outputMode());
          exportIncomeStatement(result, output.pdfOutPath());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runChangesInEquityCommand(
      BookAccess bookAccess, ChangesInEquityQuery query, CliCommand.ReportOutput output) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.changesInEquity(bookAccess, query),
        output.outputMode(),
        result -> {
          responseWriter.writeChangesInEquityResult(result, output.outputMode());
          exportChangesInEquity(result, output.pdfOutPath());
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  private void exportAccountBalance(AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountBalanceResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportAccountBalance(outputPath, reported.snapshot()));
      case AccountBalanceResult.Rejected _ -> {}
    }
  }

  private void exportTrialBalance(TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case TrialBalanceResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportTrialBalance(outputPath, reported.report()));
      case TrialBalanceResult.Rejected _ -> {}
    }
  }

  private void exportAccountLedger(AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case AccountLedgerResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportAccountLedger(outputPath, reported.report()));
      case AccountLedgerResult.Rejected _ -> {}
    }
  }

  private void exportPeriodSummary(PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case PeriodSummaryResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportPeriodSummary(outputPath, reported.report()));
      case PeriodSummaryResult.Rejected _ -> {}
    }
  }

  private void exportFinancialPosition(FinancialPositionResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case FinancialPositionResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportFinancialPosition(outputPath, reported.report()));
      case FinancialPositionResult.Rejected _ -> {}
    }
  }

  private void exportIncomeStatement(IncomeStatementResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case IncomeStatementResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportIncomeStatement(outputPath, reported.report()));
      case IncomeStatementResult.Rejected _ -> {}
    }
  }

  private void exportChangesInEquity(ChangesInEquityResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return;
    }
    switch (result) {
      case ChangesInEquityResult.Reported reported ->
          exportPdfWithDiagnostics(
              outputPath,
              () -> pdfReportExporter.exportChangesInEquity(outputPath, reported.report()));
      case ChangesInEquityResult.Rejected _ -> {}
    }
  }

  private void exportPdfWithDiagnostics(Path outputPath, Runnable pdfExport) {
    try {
      pdfExport.run();
      diagnosticsWriter.writePdfExportInfo(outputPath);
    } catch (RuntimeException exception) {
      diagnosticsWriter.writePdfExportWarning(exception);
    }
  }
}
