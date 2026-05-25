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
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
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
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.accountBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportAccountBalance(result, output.pdfOutPath());
          responseWriter.writeAccountBalanceResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.trialBalance(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportTrialBalance(result, output.pdfOutPath());
          responseWriter.writeTrialBalanceResult(result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.accountLedger(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportAccountLedger(result, output.pdfOutPath());
          responseWriter.writeAccountLedgerResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.periodSummary(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportPeriodSummary(result, output.pdfOutPath());
          responseWriter.writePeriodSummaryResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runFinancialPositionCommand(
      BookAccess bookAccess, FinancialPositionQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.financialPosition(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath =
              exportFinancialPosition(result, output.pdfOutPath());
          responseWriter.writeFinancialPositionResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runIncomeStatementCommand(
      BookAccess bookAccess, IncomeStatementQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.incomeStatement(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportIncomeStatement(result, output.pdfOutPath());
          responseWriter.writeIncomeStatementResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runChangesInEquityCommand(
      BookAccess bookAccess, ChangesInEquityQuery query, CliCommand.ReportOutput output) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), responseWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.changesInEquity(bookAccess, query),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportChangesInEquity(result, output.pdfOutPath());
          responseWriter.writeChangesInEquityResult(
              result, output.outputMode(), exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  private @Nullable Path exportAccountBalance(
      AccountBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case AccountBalanceResult.Reported reported -> {
        return exportPdf(
            outputPath,
            () -> pdfReportExporter.exportAccountBalance(outputPath, reported.snapshot()));
      }
      case AccountBalanceResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportTrialBalance(TrialBalanceResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case TrialBalanceResult.Reported reported -> {
        return exportPdf(
            outputPath, () -> pdfReportExporter.exportTrialBalance(outputPath, reported.report()));
      }
      case TrialBalanceResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportAccountLedger(
      AccountLedgerResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case AccountLedgerResult.Reported reported -> {
        return exportPdf(
            outputPath, () -> pdfReportExporter.exportAccountLedger(outputPath, reported.report()));
      }
      case AccountLedgerResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportPeriodSummary(
      PeriodSummaryResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case PeriodSummaryResult.Reported reported -> {
        return exportPdf(
            outputPath, () -> pdfReportExporter.exportPeriodSummary(outputPath, reported.report()));
      }
      case PeriodSummaryResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportFinancialPosition(
      FinancialPositionResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case FinancialPositionResult.Reported reported -> {
        return exportPdf(
            outputPath,
            () -> pdfReportExporter.exportFinancialPosition(outputPath, reported.report()));
      }
      case FinancialPositionResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportIncomeStatement(
      IncomeStatementResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case IncomeStatementResult.Reported reported -> {
        return exportPdf(
            outputPath,
            () -> pdfReportExporter.exportIncomeStatement(outputPath, reported.report()));
      }
      case IncomeStatementResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportChangesInEquity(
      ChangesInEquityResult result, @Nullable Path outputPath) {
    if (outputPath == null) {
      return null;
    }
    switch (result) {
      case ChangesInEquityResult.Reported reported -> {
        return exportPdf(
            outputPath,
            () -> pdfReportExporter.exportChangesInEquity(outputPath, reported.report()));
      }
      case ChangesInEquityResult.Rejected _ -> {
        return null;
      }
    }
  }

  private @Nullable Path exportPdf(Path outputPath, Runnable pdfExport) {
    try {
      pdfExport.run();
      return outputPath.toAbsolutePath().normalize();
    } catch (RuntimeException exception) {
      diagnosticsWriter.writePdfExportWarning(exception);
      return null;
    }
  }

  private void writePdfExportInfo(OutputMode outputMode, @Nullable Path outputPath) {
    if (outputPath != null && outputMode != OutputMode.JSON) {
      diagnosticsWriter.writePdfExportInfo(outputPath);
    }
  }
}
