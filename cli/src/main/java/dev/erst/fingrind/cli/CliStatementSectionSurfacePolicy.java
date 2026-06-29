package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.EffectiveDateRange;

/** Shared section renderability and comparative-reference rules for statement surfaces. */
final class CliStatementSectionSurfacePolicy {
  private CliStatementSectionSurfacePolicy() {}

  static boolean hasRenderableFinancialPositionSection(FinancialPositionSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }

  static boolean hasRenderableIncomeStatementSection(IncomeStatementSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }

  static boolean hasRenderableCashFlowSection(CashFlowSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }

  static boolean hasComparativeReference(EffectiveDateRange comparativeEffectiveDateRange) {
    return comparativeEffectiveDateRange.effectiveDateFrom().isPresent()
        || comparativeEffectiveDateRange.effectiveDateTo().isPresent();
  }
}
