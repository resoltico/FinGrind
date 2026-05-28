package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Executes reporting CLI commands and exports optional PDF artifacts. */
final class CliReportCommandExecutor {
  private final CliReportResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliDiagnosticsWriter diagnosticsWriter;
  private final CliBookReadWorkflow readWorkflow;
  private final CliPdfReportExporter pdfReportExporter;

  CliReportCommandExecutor(
      CliReportResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliDiagnosticsWriter diagnosticsWriter,
      CliBookReadWorkflow readWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.diagnosticsWriter = Objects.requireNonNull(diagnosticsWriter, "diagnosticsWriter");
    this.readWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.accountBalance(bookAccess, query),
        CliReportResultAccess::accountBalanceSnapshot,
        pdfReportExporter::exportAccountBalance,
        responseWriter::writeAccountBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.trialBalance(bookAccess, query),
        CliReportResultAccess::trialBalanceReport,
        pdfReportExporter::exportTrialBalance,
        responseWriter::writeTrialBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.accountLedger(bookAccess, query),
        CliReportResultAccess::accountLedgerReport,
        pdfReportExporter::exportAccountLedger,
        responseWriter::writeAccountLedgerResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.periodSummary(bookAccess, query),
        CliReportResultAccess::periodSummaryReport,
        pdfReportExporter::exportPeriodSummary,
        responseWriter::writePeriodSummaryResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runFinancialPositionCommand(
      BookAccess bookAccess, FinancialPositionQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.financialPosition(bookAccess, query),
        CliReportResultAccess::financialPositionReport,
        pdfReportExporter::exportFinancialPosition,
        responseWriter::writeFinancialPositionResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runIncomeStatementCommand(
      BookAccess bookAccess, IncomeStatementQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.incomeStatement(bookAccess, query),
        CliReportResultAccess::incomeStatementReport,
        pdfReportExporter::exportIncomeStatement,
        responseWriter::writeIncomeStatementResult,
        CliReportExitCodes::exitCodeFor);
  }

  int runChangesInEquityCommand(
      BookAccess bookAccess, ChangesInEquityQuery query, CliCommand.ReportOutput output) {
    return runReportCommand(
        bookAccess,
        output,
        () -> readWorkflow.changesInEquity(bookAccess, query),
        CliReportResultAccess::changesInEquityReport,
        pdfReportExporter::exportChangesInEquity,
        responseWriter::writeChangesInEquityResult,
        CliReportExitCodes::exitCodeFor);
  }

  private <RESULT, REPORTED> int runReportCommand(
      BookAccess bookAccess,
      CliCommand.ReportOutput output,
      Supplier<ContractDecision<RESULT>> resultSupplier,
      Function<RESULT, @Nullable REPORTED> reportedValue,
      ReportPdfExporter<REPORTED> pdfExporter,
      ReportResultWriter<RESULT> writeResult,
      ToIntFunction<RESULT> successExitCode) {
    return runPromptedReportCommand(
        bookAccess,
        output,
        resultSupplier,
        result -> exportReportedResult(result, output.pdfOutPath(), reportedValue, pdfExporter),
        (result, exportedArtifactPath) ->
            writeResult.write(result, output.outputMode(), exportedArtifactPath),
        successExitCode);
  }

  private <RESULT> int runPromptedReportCommand(
      BookAccess bookAccess,
      CliCommand.ReportOutput output,
      Supplier<ContractDecision<RESULT>> resultSupplier,
      Function<RESULT, @Nullable Path> exportAction,
      BiConsumer<RESULT, @Nullable Path> writeResult,
      ToIntFunction<RESULT> successExitCode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                output.outputMode(), bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, output.outputMode(), failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        resultSupplier.get(),
        output.outputMode(),
        result -> {
          @Nullable Path exportedArtifactPath = exportAction.apply(result);
          writeResult.accept(result, exportedArtifactPath);
          writePdfExportInfo(output.outputMode(), exportedArtifactPath);
        },
        successExitCode,
        failureWriter);
  }

  private <RESULT, REPORTED> @Nullable Path exportReportedResult(
      RESULT result,
      @Nullable Path outputPath,
      Function<RESULT, @Nullable REPORTED> reportedValue,
      ReportPdfExporter<REPORTED> pdfExporter) {
    if (outputPath == null) {
      return null;
    }
    REPORTED reported = reportedValue.apply(result);
    return reported == null
        ? null
        : exportPdf(outputPath, path -> pdfExporter.export(path, reported));
  }

  private @Nullable Path exportPdf(Path outputPath, Consumer<Path> pdfExport) {
    pdfExport.accept(outputPath);
    return outputPath.toAbsolutePath().normalize();
  }

  private void writePdfExportInfo(OutputMode outputMode, @Nullable Path outputPath) {
    if (outputPath != null && outputMode != OutputMode.JSON) {
      diagnosticsWriter.writePdfExportInfo(outputPath);
    }
  }

  /** Exports one reported read-side value into one PDF artifact path. */
  @FunctionalInterface
  private interface ReportPdfExporter<REPORTED> {
    /** Writes one reported value into the requested PDF artifact path. */
    void export(Path outputPath, REPORTED reported);
  }

  /** Writes one report-family result through the chosen output mode and artifact context. */
  @FunctionalInterface
  private interface ReportResultWriter<RESULT> {
    /** Publishes one resolved report result and any exported artifact path. */
    void write(RESULT result, OutputMode outputMode, @Nullable Path exportedArtifactPath);
  }
}
