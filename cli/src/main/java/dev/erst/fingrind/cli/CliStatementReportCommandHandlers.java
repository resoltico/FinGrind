package dev.erst.fingrind.cli;

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
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;

/** Builds configured handlers for financial statement report families. */
final class CliStatementReportCommandHandlers {
  private CliStatementReportCommandHandlers() {}

  static CliConfiguredReportHandler<
          FinancialPositionQuery, FinancialPositionResult, FinancialPositionReport>
      financialPosition(CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::financialPosition,
        FinancialPositionReportModelBuilder::buildModel,
        responseWriter::writeFinancialPositionResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          IncomeStatementQuery, IncomeStatementResult, IncomeStatementReport>
      incomeStatement(CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::incomeStatement,
        IncomeStatementReportModelBuilder::buildModel,
        responseWriter::writeIncomeStatementResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          CashFlowStatementQuery, CashFlowStatementResult, CashFlowStatementReport>
      cashFlowStatement(CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::cashFlowStatement,
        CashFlowStatementReportModelBuilder::buildModel,
        responseWriter::writeCashFlowStatementResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          ChangesInEquityQuery, ChangesInEquityResult, ChangesInEquityReport>
      changesInEquity(CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::changesInEquity,
        ChangesInEquityReportModelBuilder::buildModel,
        responseWriter::writeChangesInEquityResult,
        CliReportExitCodes::exitCodeFor);
  }
}
