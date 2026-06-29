package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Owns accounting-equation enforcement for financial-position statement projections. */
final class FinancialPositionEquationSupport {
  private FinancialPositionEquationSupport() {}

  static void assertAccountingEquation(List<FinancialPositionSectionView> sections) {
    Map<CurrencyUnit, Long> assetTotals = signedSectionTotals(section(sections, AccountType.ASSET));
    Map<CurrencyUnit, Long> liabilityTotals =
        signedSectionTotals(section(sections, AccountType.LIABILITY));
    Map<CurrencyUnit, Long> equityTotals =
        signedSectionTotals(section(sections, AccountType.EQUITY));
    for (CurrencyUnit currencyUnit :
        ReportingBalanceSupport.currencyUnits(assetTotals, liabilityTotals, equityTotals)) {
      long signedTotal =
          Math.addExact(
              assetTotals.getOrDefault(currencyUnit, 0L),
              Math.addExact(
                  liabilityTotals.getOrDefault(currencyUnit, 0L),
                  equityTotals.getOrDefault(currencyUnit, 0L)));
      if (signedTotal != 0L) {
        throw new IllegalStateException(
            "Financial position violates the accounting equation for currency "
                + currencyUnit.code()
                + ".");
      }
    }
  }

  private static FinancialPositionSectionView section(
      List<FinancialPositionSectionView> sections, AccountType accountType) {
    return sections.stream()
        .filter(section -> section.accountType() == accountType)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing statement-of-financial-position section: " + accountType));
  }

  private static Map<CurrencyUnit, Long> signedSectionTotals(FinancialPositionSectionView section) {
    return Map.copyOf(
        section.totals().stream()
            .collect(
                Collectors.toConcurrentMap(
                    balance -> balance.netAmount().currencyUnit(),
                    ReportingBalanceSupport::signedMinorUnits,
                    Math::addExact)));
  }
}
