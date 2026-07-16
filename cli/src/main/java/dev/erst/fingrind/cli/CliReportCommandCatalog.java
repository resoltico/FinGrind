package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryReportResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TaxObligationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Builds one configured handler per report family from the shared read workflow and writer. */
record CliReportCommandCatalog(
    CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
  CliConfiguredReportHandler<AccountBalanceQuery, AccountBalanceResult, AccountBalanceSnapshot>
      accountBalance() {
    return configured(
        readWorkflow::accountBalance,
        AccountBalanceReportModelBuilder::buildModel,
        responseWriter::writeAccountBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<TrialBalanceQuery, TrialBalanceResult, TrialBalanceReport>
      trialBalance() {
    return configured(
        readWorkflow::trialBalance,
        TrialBalanceReportModelBuilder::buildModel,
        responseWriter::writeTrialBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<AccountLedgerQuery, AccountLedgerResult, AccountLedgerReport>
      accountLedger() {
    return configured(
        readWorkflow::accountLedger,
        AccountLedgerReportModelBuilder::buildModel,
        responseWriter::writeAccountLedgerResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<PeriodSummaryQuery, PeriodSummaryResult, PeriodSummaryReport>
      periodSummary() {
    return configured(
        readWorkflow::periodSummary,
        PeriodSummaryReportModelBuilder::buildModel,
        responseWriter::writePeriodSummaryResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<
          FinancialPositionQuery, FinancialPositionResult, FinancialPositionReport>
      financialPosition() {
    return CliStatementReportCommandHandlers.financialPosition(readWorkflow, responseWriter);
  }

  CliConfiguredReportHandler<IncomeStatementQuery, IncomeStatementResult, IncomeStatementReport>
      incomeStatement() {
    return CliStatementReportCommandHandlers.incomeStatement(readWorkflow, responseWriter);
  }

  CliConfiguredReportHandler<
          CashFlowStatementQuery, CashFlowStatementResult, CashFlowStatementReport>
      cashFlowStatement() {
    return CliStatementReportCommandHandlers.cashFlowStatement(readWorkflow, responseWriter);
  }

  CliConfiguredReportHandler<ChangesInEquityQuery, ChangesInEquityResult, ChangesInEquityReport>
      changesInEquity() {
    return CliStatementReportCommandHandlers.changesInEquity(readWorkflow, responseWriter);
  }

  CliConfiguredReportHandler<TaxObligationQuery, TaxObligationResult, TaxObligationReport>
      taxObligation() {
    return configuredTax(
        readWorkflow::taxObligation,
        result -> result.fold(TaxObligationResult.Reported::report, rejected -> null),
        TaxObligationReportModelBuilder::buildModel,
        responseWriter::writeTaxObligationResult,
        CliBookQueryExitCodes::exitCodeFor);
  }

  static <QUERY, RESULT extends BookQueryReportResult<REPORTED>, REPORTED>
      CliConfiguredReportHandler<QUERY, RESULT, REPORTED> configured(
          CliConfiguredReportHandler.WorkflowCall<QUERY, RESULT> workflowCall,
          Function<REPORTED, ReportModel> reportModelBuilder,
          CliConfiguredReportHandler.ResultWriter<RESULT> resultWriter,
          ToIntFunction<RESULT> successExitCode) {
    return new CliConfiguredReportHandler<>(
        workflowCall,
        BookQueryReportResult::reported,
        reportModelBuilder,
        resultWriter,
        successExitCode);
  }

  private static <QUERY, RESULT, REPORTED>
      CliConfiguredReportHandler<QUERY, RESULT, REPORTED> configuredTax(
          CliConfiguredReportHandler.WorkflowCall<QUERY, RESULT> workflowCall,
          CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
          Function<REPORTED, ReportModel> reportModelBuilder,
          CliConfiguredReportHandler.ResultWriter<RESULT> resultWriter,
          ToIntFunction<RESULT> successExitCode) {
    return new CliConfiguredReportHandler<>(
        workflowCall, reportedValue, reportModelBuilder, resultWriter, successExitCode);
  }
}
