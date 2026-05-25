package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRules;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Computes signed profit-and-loss contributions from declared account currency totals. */
final class ProfitAndLossContributionCalculator {
  private final Supplier<KernelAccountingRules> accountingRulesSupplier;

  ProfitAndLossContributionCalculator(Supplier<KernelAccountingRules> accountingRulesSupplier) {
    this.accountingRulesSupplier =
        Objects.requireNonNull(accountingRulesSupplier, "accountingRulesSupplier");
  }

  Map<CurrencyUnit, Long> contributionMap(List<AccountCurrencyTotals> accountTotals) {
    Objects.requireNonNull(accountTotals, "accountTotals");
    KernelAccountingRules accountingRules =
        KernelAccountingRules.requireAccountingRules(accountingRulesSupplier.get());
    return Map.copyOf(
        accountTotals.stream()
            .filter(
                accountTotal ->
                    accountingRules
                        .resultTransferPolicy()
                        .closesAccountType(accountTotal.account().accountType()))
            .filter(accountTotal -> accountTotal.balance().balanceSide() != BalanceSide.ZERO)
            .collect(
                Collectors.toConcurrentMap(
                    AccountCurrencyTotals::currencyUnit,
                    ProfitAndLossContributionCalculator::contributionMinorUnits,
                    Math::addExact)));
  }

  private static long contributionMinorUnits(AccountCurrencyTotals accountTotal) {
    RegisteredAccount account = accountTotal.account();
    CurrencyBalance balance = accountTotal.balance();
    return AccountSemantics.profitAndLossContributionMinorUnits(
        account.accountType(),
        account.accountRole(),
        balance.balanceSide(),
        balance.netAmount().minorUnits());
  }
}
