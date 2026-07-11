package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
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
  private final CliReportCommandCatalog handlers;
  private final CliFailureResponseWriter failureWriter;
  private final CliPdfReportExporter pdfReportExporter;

  CliReportCommandExecutor(
      CliReportResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookReadWorkflow readWorkflow,
      CliPdfReportExporter pdfReportExporter) {
    CliReportResponseWriter requiredResponseWriter =
        Objects.requireNonNull(responseWriter, "responseWriter");
    CliBookReadWorkflow requiredReadWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
    this.handlers = new CliReportCommandCatalog(requiredReadWorkflow, requiredResponseWriter);
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter");
  }

  int runAccountBalanceCommand(
      BookAccess bookAccess, AccountBalanceQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.accountBalance());
  }

  int runTrialBalanceCommand(
      BookAccess bookAccess, TrialBalanceQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.trialBalance());
  }

  int runAccountLedgerCommand(
      BookAccess bookAccess, AccountLedgerQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.accountLedger());
  }

  int runPeriodSummaryCommand(
      BookAccess bookAccess, PeriodSummaryQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.periodSummary());
  }

  int runFinancialPositionCommand(
      BookAccess bookAccess, FinancialPositionQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.financialPosition());
  }

  int runIncomeStatementCommand(
      BookAccess bookAccess, IncomeStatementQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.incomeStatement());
  }

  int runInventoryValuationCommand(
      BookAccess bookAccess, InventoryValuationQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.inventoryValuation());
  }

  int runCashFlowStatementCommand(
      BookAccess bookAccess, CashFlowStatementQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.cashFlowStatement());
  }

  int runChangesInEquityCommand(
      BookAccess bookAccess, ChangesInEquityQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.changesInEquity());
  }

  int runTaxObligationCommand(
      BookAccess bookAccess, TaxObligationQuery query, CliReportOutput output) {
    return runConfiguredReportCommand(bookAccess, query, output, handlers.taxObligation());
  }

  private <QUERY, RESULT, REPORTED> int runConfiguredReportCommand(
      BookAccess bookAccess,
      QUERY query,
      CliReportOutput output,
      CliConfiguredReportHandler<QUERY, RESULT, REPORTED> handler) {
    return runReportCommand(
        bookAccess,
        output,
        () -> handler.workflowCall().run(bookAccess, query),
        handler.reportedValue(),
        handler.reportModelBuilder(),
        handler.resultWriter(),
        handler.successExitCode());
  }

  private <RESULT, REPORTED> int runReportCommand(
      BookAccess bookAccess,
      CliReportOutput output,
      Supplier<ContractDecision<RESULT>> resultSupplier,
      CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
      Function<REPORTED, ReportModel> reportModelBuilder,
      CliConfiguredReportHandler.ResultWriter<RESULT> writeResult,
      ToIntFunction<RESULT> successExitCode) {
    return runPromptedReportCommand(
        bookAccess,
        output,
        resultSupplier,
        result ->
            exportReportedResult(result, output.pdfOutPath(), reportedValue, reportModelBuilder),
        (result, exportedArtifactPath) ->
            writeResult.write(result, output.outputMode(), exportedArtifactPath),
        successExitCode);
  }

  private <RESULT> int runPromptedReportCommand(
      BookAccess bookAccess,
      CliReportOutput output,
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
                        failure, failureWriter, output.outputMode()));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        resultSupplier.get(),
        result -> {
          @Nullable Path exportedArtifactPath = exportAction.apply(result);
          writeResult.accept(result, exportedArtifactPath);
        },
        successExitCode,
        failureWriter,
        output.outputMode());
  }

  private <RESULT, REPORTED> @Nullable Path exportReportedResult(
      RESULT result,
      @Nullable Path outputPath,
      CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
      Function<REPORTED, ReportModel> reportModelBuilder) {
    if (outputPath == null) {
      return null;
    }
    REPORTED reported = reportedValue.apply(result);
    return reported == null
        ? null
        : exportPdf(
            outputPath, path -> pdfReportExporter.export(path, reportModelBuilder.apply(reported)));
  }

  private @Nullable Path exportPdf(Path outputPath, Consumer<Path> pdfExport) {
    pdfExport.accept(outputPath);
    return outputPath.toAbsolutePath().normalize();
  }
}
