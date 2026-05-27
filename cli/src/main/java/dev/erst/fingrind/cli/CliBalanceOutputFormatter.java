package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;

/** Shared balance text and CSV formatting helpers. */
final class CliBalanceOutputFormatter {
  private CliBalanceOutputFormatter() {}

  static List<String> balanceTextRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(balance.debitTotal()),
        CliQueryScopeText.displayMoney(balance.creditTotal()),
        CliQueryScopeText.displayMoney(balance.netAmount()),
        displayBalanceSideLabel(balance.balanceSide()));
  }

  static List<String> balanceCsvRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(balance.debitTotal()),
        CliQueryScopeText.displayMoney(balance.creditTotal()),
        CliQueryScopeText.displayMoney(balance.netAmount()),
        balance.balanceSide().wireValue());
  }

  static String joinedBalances(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliBalanceOutputFormatter::displayBalanceText)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  static String displayBalance(CurrencyBalance balance) {
    return balance.netAmount().currencyUnit().code()
        + " "
        + CliQueryScopeText.displayMoney(balance.netAmount())
        + " "
        + balance.balanceSide().wireValue();
  }

  static String displayBalanceText(CurrencyBalance balance) {
    return balance.netAmount().currencyUnit().code()
        + " "
        + CliQueryScopeText.displayMoney(balance.netAmount())
        + " "
        + displayBalanceSideLabel(balance.balanceSide());
  }

  static String displayBalanceSideLabel(BalanceSide balanceSide) {
    return switch (balanceSide) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
      case ZERO -> "Zero";
    };
  }

  static String displayBalanceStateLabel(boolean balanced) {
    return balanced ? "Balanced" : "Imbalanced";
  }
}
