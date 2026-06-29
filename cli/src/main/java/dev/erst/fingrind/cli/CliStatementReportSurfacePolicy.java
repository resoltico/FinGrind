package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;

/** Statement-report surface policy shared by text, CSV, and payload renderers. */
final class CliStatementReportSurfacePolicy {
  private CliStatementReportSurfacePolicy() {}

  static boolean hasComparative(FinancialPositionReport report) {
    return CliStatementSectionSurfacePolicy.hasComparativeReference(
            report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(FinancialPositionReport report) {
    return report.comparativeSections().stream()
        .anyMatch(CliStatementSectionSurfacePolicy::hasRenderableFinancialPositionSection);
  }

  static boolean hasComparative(IncomeStatementReport report) {
    return CliStatementSectionSurfacePolicy.hasComparativeReference(
            report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(IncomeStatementReport report) {
    return report.comparativeSections().stream()
            .anyMatch(CliStatementSectionSurfacePolicy::hasRenderableIncomeStatementSection)
        || !report.comparativeNetIncomeTotals().isEmpty();
  }

  static boolean hasComparative(ChangesInEquityReport report) {
    return CliStatementSectionSurfacePolicy.hasComparativeReference(
            report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparative(CashFlowStatementReport report) {
    return CliStatementSectionSurfacePolicy.hasComparativeReference(
            report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(ChangesInEquityReport report) {
    return !report.comparativeRows().isEmpty()
        || !report.comparativeOpeningTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingTotals().isEmpty();
  }

  static boolean hasComparativeData(CashFlowStatementReport report) {
    return report.comparativeSections().stream()
            .anyMatch(CliStatementSectionSurfacePolicy::hasRenderableCashFlowSection)
        || !report.comparativeOpeningCashTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingCashTotals().isEmpty();
  }

  static boolean hasCurrent(ChangesInEquityReport report) {
    return !report.rows().isEmpty()
        || !report.openingTotals().isEmpty()
        || !report.movementTotals().isEmpty()
        || !report.closingTotals().isEmpty();
  }

  static boolean hasCurrent(CashFlowStatementReport report) {
    return report.sections().stream()
            .anyMatch(CliStatementSectionSurfacePolicy::hasRenderableCashFlowSection)
        || !report.openingCashTotals().isEmpty()
        || !report.movementTotals().isEmpty()
        || !report.closingCashTotals().isEmpty();
  }
}
