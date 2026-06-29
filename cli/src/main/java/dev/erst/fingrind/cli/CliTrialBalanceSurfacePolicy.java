package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;

/** Trial-balance surface policy shared by text, CSV, and payload renderers. */
final class CliTrialBalanceSurfacePolicy {
  private CliTrialBalanceSurfacePolicy() {}

  static boolean hasComparative(TrialBalanceReport report) {
    return CliStatementSectionSurfacePolicy.hasComparativeReference(
            report.comparativeEffectiveDateRange())
        || hasComparativeData(report);
  }

  static boolean hasComparativeData(TrialBalanceReport report) {
    return !report.comparativeRows().isEmpty() || !report.comparativeTotals().isEmpty();
  }

  static boolean hasCurrent(TrialBalanceReport report) {
    return !report.rows().isEmpty() || !report.totals().isEmpty();
  }
}
