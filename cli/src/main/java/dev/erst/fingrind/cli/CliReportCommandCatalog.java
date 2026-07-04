package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
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
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
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
        result -> result.fold(AccountBalanceResult.Reported::snapshot, rejected -> null),
        AccountBalanceReportModelBuilder::buildModel,
        responseWriter::writeAccountBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<TrialBalanceQuery, TrialBalanceResult, TrialBalanceReport>
      trialBalance() {
    return configured(
        readWorkflow::trialBalance,
        result -> result.fold(TrialBalanceResult.Reported::report, rejected -> null),
        TrialBalanceReportModelBuilder::buildModel,
        responseWriter::writeTrialBalanceResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<AccountLedgerQuery, AccountLedgerResult, AccountLedgerReport>
      accountLedger() {
    return configured(
        readWorkflow::accountLedger,
        result -> result.fold(AccountLedgerResult.Reported::report, rejected -> null),
        AccountLedgerReportModelBuilder::buildModel,
        responseWriter::writeAccountLedgerResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<PeriodSummaryQuery, PeriodSummaryResult, PeriodSummaryReport>
      periodSummary() {
    return configured(
        readWorkflow::periodSummary,
        result -> result.fold(PeriodSummaryResult.Reported::report, rejected -> null),
        PeriodSummaryReportModelBuilder::buildModel,
        responseWriter::writePeriodSummaryResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<
          FinancialPositionQuery, FinancialPositionResult, FinancialPositionReport>
      financialPosition() {
    return configured(
        readWorkflow::financialPosition,
        result -> result.fold(FinancialPositionResult.Reported::report, rejected -> null),
        FinancialPositionReportModelBuilder::buildModel,
        responseWriter::writeFinancialPositionResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<IncomeStatementQuery, IncomeStatementResult, IncomeStatementReport>
      incomeStatement() {
    return configured(
        readWorkflow::incomeStatement,
        result -> result.fold(IncomeStatementResult.Reported::report, rejected -> null),
        IncomeStatementReportModelBuilder::buildModel,
        responseWriter::writeIncomeStatementResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<
          CashFlowStatementQuery, CashFlowStatementResult, CashFlowStatementReport>
      cashFlowStatement() {
    return configured(
        readWorkflow::cashFlowStatement,
        result -> result.fold(CashFlowStatementResult.Reported::report, rejected -> null),
        CashFlowStatementReportModelBuilder::buildModel,
        responseWriter::writeCashFlowStatementResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<ChangesInEquityQuery, ChangesInEquityResult, ChangesInEquityReport>
      changesInEquity() {
    return configured(
        readWorkflow::changesInEquity,
        result -> result.fold(ChangesInEquityResult.Reported::report, rejected -> null),
        ChangesInEquityReportModelBuilder::buildModel,
        responseWriter::writeChangesInEquityResult,
        CliReportExitCodes::exitCodeFor);
  }

  CliConfiguredReportHandler<TaxObligationQuery, TaxObligationResult, TaxObligationReport>
      taxObligation() {
    return configured(
        readWorkflow::taxObligation,
        result -> result.fold(TaxObligationResult.Reported::report, rejected -> null),
        TaxObligationReportModelBuilder::buildModel,
        responseWriter::writeTaxObligationResult,
        CliBookQueryExitCodes::exitCodeFor);
  }

  private static <QUERY, RESULT, REPORTED>
      CliConfiguredReportHandler<QUERY, RESULT, REPORTED> configured(
          CliConfiguredReportHandler.WorkflowCall<QUERY, RESULT> workflowCall,
          CliConfiguredReportHandler.ReportedValue<RESULT, REPORTED> reportedValue,
          Function<REPORTED, ReportModel> reportModelBuilder,
          CliConfiguredReportHandler.ResultWriter<RESULT> resultWriter,
          ToIntFunction<RESULT> successExitCode) {
    return new CliConfiguredReportHandler<>(
        workflowCall, reportedValue, reportModelBuilder, resultWriter, successExitCode);
  }
}
