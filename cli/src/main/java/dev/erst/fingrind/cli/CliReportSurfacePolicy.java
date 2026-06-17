package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.core.EffectiveDateRange;

/** Shared report-surface policy that keeps text and machine renderers aligned. */
final class CliReportSurfacePolicy {
  private CliReportSurfacePolicy() {}

  static boolean hasComparative(TrialBalanceReport report) {
    return hasComparativeReference(report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(TrialBalanceReport report) {
    return !report.comparativeRows().isEmpty() || !report.comparativeTotals().isEmpty();
  }

  static boolean hasCurrent(TrialBalanceReport report) {
    return !report.rows().isEmpty() || !report.totals().isEmpty();
  }

  static boolean hasComparative(FinancialPositionReport report) {
    return hasComparativeReference(report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(FinancialPositionReport report) {
    return report.comparativeSections().stream()
        .anyMatch(CliReportSurfacePolicy::hasRenderableFinancialPositionSection);
  }

  static boolean hasComparative(IncomeStatementReport report) {
    return hasComparativeReference(report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(IncomeStatementReport report) {
    for (IncomeStatementSection section : report.comparativeSections()) {
      if (hasRenderableIncomeStatementSection(section)) {
        return true;
      }
    }
    return !report.comparativeNetIncomeTotals().isEmpty();
  }

  static boolean hasComparative(ChangesInEquityReport report) {
    return hasComparativeReference(report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(ChangesInEquityReport report) {
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

  static boolean hasComparativeReference(EffectiveDateRange comparativeEffectiveDateRange) {
    return comparativeEffectiveDateRange.effectiveDateFrom().isPresent()
        || comparativeEffectiveDateRange.effectiveDateTo().isPresent();
  }
}
