package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared balance aggregation and signed-amount helpers for bookkeeping statement projections. */
final class ReportingBalanceSupport {
  private ReportingBalanceSupport() {}

  static List<CurrencyBalance> aggregateBalances(List<CurrencyBalance> balances) {
    Map<CurrencyUnit, SignedDebitCreditTotals> totalsByCurrency =
        balances.stream()
            .collect(
                Collectors.toConcurrentMap(
                    balance -> balance.netAmount().currencyUnit(),
                    balance ->
                        new SignedDebitCreditTotals(
                            balance.debitTotal().minorUnits(), balance.creditTotal().minorUnits()),
                    SignedDebitCreditTotals::plus));
    return totalsByCurrency.entrySet().stream()
        .map(entry -> entry.getValue().balance(entry.getKey()))
        .sorted(ReportingRowOrdering.BALANCE_ORDER)
        .toList();
  }

  @SafeVarargs
  static List<CurrencyUnit> currencyUnits(Map<CurrencyUnit, ?>... maps) {
    SortedSet<CurrencyUnit> ordered = new TreeSet<>(Comparator.comparing(CurrencyUnit::code));
    for (Map<CurrencyUnit, ?> map : maps) {
      ordered.addAll(map.keySet());
    }
    return List.copyOf(ordered);
  }

  static CurrencyBalance balanceOrZero(
      @Nullable AccountCurrencyTotals accountTotal, CurrencyUnit currencyUnit) {
    return accountTotal == null
        ? BalanceMath.currencyBalance(currencyUnit, 0L, 0L)
        : accountTotal.balance();
  }

  static CurrencyBalance signedBalance(CurrencyUnit currencyUnit, long signedMinorUnits) {
    return switch (BalanceDirection.from(signedMinorUnits)) {
      case CREDIT -> BalanceMath.currencyBalance(currencyUnit, 0L, signedMinorUnits);
      case DEBIT -> BalanceMath.currencyBalance(currencyUnit, Math.absExact(signedMinorUnits), 0L);
      case ZERO -> BalanceMath.currencyBalance(currencyUnit, 0L, 0L);
    };
  }

  /** Direction carried by one signed reporting amount. */
  private enum BalanceDirection {
    CREDIT,
    DEBIT,
    ZERO;

    private static BalanceDirection from(long signedMinorUnits) {
      if (signedMinorUnits == 0L) {
        return ZERO;
      }
      return signedMinorUnits > 0L ? CREDIT : DEBIT;
    }
  }

  static long signedMinorUnits(CurrencyBalance balance) {
    return switch (balance.balanceSide()) {
      case DEBIT -> balance.netAmount().minorUnits();
      case CREDIT -> Math.negateExact(balance.netAmount().minorUnits());
      case ZERO -> 0L;
    };
  }

  /** Running debit and credit totals for one currency during statement aggregation. */
  private record SignedDebitCreditTotals(long debitTotalMinor, long creditTotalMinor) {
    private SignedDebitCreditTotals plus(SignedDebitCreditTotals other) {
      return new SignedDebitCreditTotals(
          Math.addExact(debitTotalMinor, other.debitTotalMinor()),
          Math.addExact(creditTotalMinor, other.creditTotalMinor()));
    }

    private CurrencyBalance balance(CurrencyUnit currencyUnit) {
      return BalanceMath.currencyBalance(currencyUnit, debitTotalMinor, creditTotalMinor);
    }
  }
}
