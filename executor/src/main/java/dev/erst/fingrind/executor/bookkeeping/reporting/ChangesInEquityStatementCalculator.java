package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolver;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.DerivedEquityLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Computes statement-of-changes-in-equity views from opening, movement, and closing slices. */
final class ChangesInEquityStatementCalculator {
  private final ReportingContext context;
  private final ProfitAndLossContributionCalculator profitAndLossContributionCalculator;

  ChangesInEquityStatementCalculator(ReportingContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.profitAndLossContributionCalculator =
        new ProfitAndLossContributionCalculator(context::accountingRules);
  }

  ChangesInEquityView view(ChangesInEquityCriteria criteria) {
    BookIdentity bookIdentity = context.bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.ALL_POSTING_KINDS;
    EffectiveDateRange comparativeRange =
        ComparativeRangeResolver.period(
            bookIdentity,
            criteria.effectiveDateFrom(),
            criteria.effectiveDateTo(),
            criteria.comparativeSelection(),
            context.accountingRules().statementComparativePolicy());
    ChangesInEquitySnapshot currentSnapshot =
        snapshot(
            bookIdentity,
            criteria.effectiveDateFrom(),
            criteria.effectiveDateTo(),
            postingCoverage);
    ChangesInEquitySnapshot comparativeSnapshot =
        comparativeSnapshot(bookIdentity, comparativeRange, postingCoverage);
    return new ChangesInEquityView(
        bookIdentity,
        criteria.effectiveDateFrom(),
        criteria.effectiveDateTo(),
        comparativeRange,
        postingCoverage,
        currentSnapshot.rows(),
        currentSnapshot.openingTotals(),
        currentSnapshot.movementTotals(),
        currentSnapshot.closingTotals(),
        comparativeSnapshot.rows(),
        comparativeSnapshot.openingTotals(),
        comparativeSnapshot.movementTotals(),
        comparativeSnapshot.closingTotals());
  }

  private ChangesInEquitySnapshot comparativeSnapshot(
      BookIdentity bookIdentity,
      EffectiveDateRange comparativeRange,
      PostingCoverage postingCoverage) {
    return PeriodComparativeRangeSupport.boundedRange(comparativeRange)
        .map(
            range ->
                snapshot(
                    bookIdentity,
                    range.effectiveDateFrom().orElseThrow(),
                    range.effectiveDateTo().orElseThrow(),
                    postingCoverage))
        .orElseGet(ChangesInEquitySnapshot::empty);
  }

  private ChangesInEquitySnapshot snapshot(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      PostingCoverage postingCoverage) {
    LocalDate dayBefore = effectiveDateFrom.minusDays(1);
    List<AccountCurrencyTotals> openingTotals =
        context.bookStore().accountTotals(EffectiveDateRange.to(dayBefore), postingCoverage);
    List<AccountCurrencyTotals> movementTotals =
        context
            .bookStore()
            .accountTotals(
                EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), postingCoverage);
    List<AccountCurrencyTotals> closingTotals =
        context.bookStore().accountTotals(EffectiveDateRange.to(effectiveDateTo), postingCoverage);
    Map<AccountCurrencyKey, AccountCurrencyTotals> openingTotalsByKey =
        indexAccountTotals(openingTotals);
    Map<AccountCurrencyKey, AccountCurrencyTotals> movementTotalsByKey =
        indexAccountTotals(movementTotals);
    Map<AccountCurrencyKey, AccountCurrencyTotals> closingTotalsByKey =
        indexAccountTotals(closingTotals);
    DerivedEquityLine currentPeriodResultLine =
        context
            .accountingRules()
            .statementPresentationPolicy()
            .currentPeriodResultLine(bookIdentity);

    List<ChangesInEquityRowView> rows = new ArrayList<>();
    for (AccountCurrencyKey key :
        orderedKeys(openingTotalsByKey, movementTotalsByKey, closingTotalsByKey)) {
      AccountCurrencyTotals closingTotal = closingTotalsByKey.get(key);
      AccountCurrencyTotals movementTotal = movementTotalsByKey.get(key);
      AccountCurrencyTotals openingTotal = openingTotalsByKey.get(key);
      RegisteredAccount account =
          closingTotal != null
              ? closingTotal.account()
              : movementTotal != null
                  ? movementTotal.account()
                  : Objects.requireNonNull(openingTotal).account();
      if (account.accountType() != AccountType.EQUITY) {
        continue;
      }
      rows.add(
          new ChangesInEquityRowView(
              account.accountCode().value(),
              account.accountName().value(),
              Optional.of(account.accountType()),
              account.accountTaxonomy().financialPositionLineClassification(),
              StatementLineKind.DECLARED_ACCOUNT,
              ReportingBalanceSupport.balanceOrZero(openingTotal, key.currencyUnit()),
              ReportingBalanceSupport.balanceOrZero(movementTotal, key.currencyUnit()),
              ReportingBalanceSupport.balanceOrZero(closingTotal, key.currencyUnit())));
    }

    Map<CurrencyUnit, Long> openingCurrentEarnings =
        profitAndLossContributionCalculator.contributionMap(openingTotals);
    Map<CurrencyUnit, Long> closingCurrentEarnings =
        profitAndLossContributionCalculator.contributionMap(closingTotals);
    ReportingBalanceSupport.currencyUnits(openingCurrentEarnings, closingCurrentEarnings)
        .forEach(
            currencyUnit -> {
              long opening = openingCurrentEarnings.getOrDefault(currencyUnit, 0L);
              long closing = closingCurrentEarnings.getOrDefault(currencyUnit, 0L);
              rows.add(
                  new ChangesInEquityRowView(
                      currentPeriodResultLine.lineCode(),
                      currentPeriodResultLine.lineName(),
                      Optional.empty(),
                      Optional.empty(),
                      StatementLineKind.CURRENT_PERIOD_RESULT,
                      ReportingBalanceSupport.signedBalance(currencyUnit, opening),
                      ReportingBalanceSupport.signedBalance(
                          currencyUnit, Math.subtractExact(closing, opening)),
                      ReportingBalanceSupport.signedBalance(currencyUnit, closing)));
            });

    rows.sort(ReportingRowOrdering.CHANGES_IN_EQUITY_ROW_ORDER);
    return new ChangesInEquitySnapshot(
        rows,
        ChangesInEquityTotals.aggregateOpeningTotals(rows),
        ChangesInEquityTotals.aggregateMovementTotals(rows),
        ChangesInEquityTotals.aggregateClosingTotals(rows));
  }

  @SafeVarargs
  private static List<AccountCurrencyKey> orderedKeys(Map<AccountCurrencyKey, ?>... maps) {
    SortedSet<AccountCurrencyKey> ordered =
        new TreeSet<>(
            Comparator.comparing((AccountCurrencyKey key) -> key.accountCode().value())
                .thenComparing(key -> key.currencyUnit().code()));
    for (Map<AccountCurrencyKey, ?> map : maps) {
      ordered.addAll(map.keySet());
    }
    return List.copyOf(ordered);
  }

  private static Map<AccountCurrencyKey, AccountCurrencyTotals> indexAccountTotals(
      List<AccountCurrencyTotals> accountTotals) {
    return Map.copyOf(
        accountTotals.stream()
            .collect(
                Collectors.toConcurrentMap(
                    accountTotal ->
                        new AccountCurrencyKey(
                            accountTotal.account().accountCode(), accountTotal.currencyUnit()),
                    Function.identity())));
  }

  private record AccountCurrencyKey(AccountCode accountCode, CurrencyUnit currencyUnit) {
    private AccountCurrencyKey {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }
  }

  private record ChangesInEquitySnapshot(
      List<ChangesInEquityRowView> rows,
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals) {
    private static ChangesInEquitySnapshot empty() {
      return new ChangesInEquitySnapshot(List.of(), List.of(), List.of(), List.of());
    }

    private ChangesInEquitySnapshot {
      rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
      openingTotals = List.copyOf(Objects.requireNonNull(openingTotals, "openingTotals"));
      movementTotals = List.copyOf(Objects.requireNonNull(movementTotals, "movementTotals"));
      closingTotals = List.copyOf(Objects.requireNonNull(closingTotals, "closingTotals"));
    }
  }
}
