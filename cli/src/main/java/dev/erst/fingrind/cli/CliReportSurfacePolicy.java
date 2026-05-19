package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;

/** Shared report-surface policy that keeps human and machine renderers aligned. */
final class CliReportSurfacePolicy {
  private CliReportSurfacePolicy() {}

  static boolean hasComparative(TrialBalanceReport report) {
    return !report.comparativeRows().isEmpty();
  }

  static boolean hasComparative(FinancialPositionReport report) {
    return report.comparativeSections().stream()
        .anyMatch(CliReportSurfacePolicy::hasRenderableFinancialPositionSection);
  }

  static boolean hasComparative(IncomeStatementReport report) {
    return report.comparativeSections().stream()
            .anyMatch(CliReportSurfacePolicy::hasRenderableIncomeStatementSection)
        || !report.comparativeNetIncomeTotals().isEmpty();
  }

  static boolean hasComparative(ChangesInEquityReport report) {
    return !report.comparativeRows().isEmpty()
        || !report.comparativeOpeningTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingTotals().isEmpty();
  }

  static boolean hasCurrent(ChangesInEquityReport report) {
    return !report.rows().isEmpty()
        || !report.openingTotals().isEmpty()
        || !report.movementTotals().isEmpty()
        || !report.closingTotals().isEmpty();
  }

  static boolean hasRenderableFinancialPositionSection(FinancialPositionSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }

  static boolean hasRenderableIncomeStatementSection(IncomeStatementSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }
}
