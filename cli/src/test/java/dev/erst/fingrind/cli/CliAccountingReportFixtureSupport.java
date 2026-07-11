package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared test support for accounting, taxonomy, and trial-balance fixture assembly. */
class CliAccountingReportFixtureSupport extends CliRequestDocumentFixtureSupport {
  protected static DeclaredAccount declaredAccount(
      String accountCode,
      String accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        fixtureAccountTaxonomy(accountType),
        active,
        declaredAt);
  }

  protected static DeclaredAccount inventoryDeclaredAccount(
      String accountCode,
      String accountName,
      String unitToken,
      int quantityScale,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.ASSET,
        inventoryTaxonomy(),
        new UnitOfMeasure(unitToken, quantityScale),
        active,
        declaredAt);
  }

  protected static DeclaredAccount inventoryDeclaredAccount(
      String accountCode, String accountName, String unitToken, int quantityScale) {
    return inventoryDeclaredAccount(
        accountCode,
        accountName,
        unitToken,
        quantityScale,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  protected static AccountTaxonomy fixtureAccountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  protected static AccountTaxonomy inventoryTaxonomy() {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.INVENTORY),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }

  protected static TrialBalanceReport trialBalanceReport(
      Optional<LocalDate> effectiveDateAsOf,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    return trialBalanceReport(
        bookIdentity(),
        effectiveDateAsOf,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        comparativeRows);
  }

  protected static TrialBalanceReport trialBalanceReport(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateAsOf,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    List<CurrencyBalance> totals = trialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = trialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        bookIdentity,
        effectiveDateAsOf,
        effectiveDateAsOf,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  private static List<CurrencyBalance> trialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  private static void mergeCurrencyBalance(
      List<CurrencyBalance> totalsByCurrency, CurrencyBalance nextBalance) {
    CurrencyUnit nextCurrency = nextBalance.debitTotal().currencyUnit();
    for (int index = 0; index < totalsByCurrency.size(); index++) {
      CurrencyBalance existingBalance = totalsByCurrency.get(index);
      if (existingBalance.debitTotal().currencyUnit().equals(nextCurrency)) {
        totalsByCurrency.set(index, sumCurrencyBalances(existingBalance, nextBalance));
        return;
      }
    }
    totalsByCurrency.add(nextBalance);
  }

  private static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return CurrencyBalance.ofTotals(
        sumMoney(left.debitTotal(), right.debitTotal()),
        sumMoney(left.creditTotal(), right.creditTotal()));
  }

  private static Money sumMoney(Money left, Money right) {
    return left.plus(right);
  }

  private static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(balance -> balance.balanceSide() == BalanceSide.ZERO);
  }
}
