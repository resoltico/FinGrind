package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolver;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes income-statement views from one bounded posting window. */
final class IncomeStatementCalculator {
  private static final List<AccountType> SECTION_ORDER =
      List.of(AccountType.REVENUE, AccountType.EXPENSE);

  private final ReportingContext context;
  private final ProfitAndLossContributionCalculator profitAndLossContributionCalculator;

  IncomeStatementCalculator(ReportingContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.profitAndLossContributionCalculator =
        new ProfitAndLossContributionCalculator(context::accountingRules);
  }

  IncomeStatementView view(IncomeStatementCriteria criteria) {
    BookIdentity bookIdentity = context.bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.NON_CLOSING_POSTINGS;
    EffectiveDateRange comparativeRange =
        ComparativeRangeResolver.period(
            bookIdentity,
            criteria.effectiveDateFrom(),
            criteria.effectiveDateTo(),
            criteria.comparativeSelection(),
            context.accountingRules().statementComparativePolicy());
    IncomeStatementSnapshot currentSnapshot =
        snapshot(
            context
                .bookStore()
                .accountTotals(
                    EffectiveDateRange.of(criteria.effectiveDateFrom(), criteria.effectiveDateTo()),
                    postingCoverage));
    IncomeStatementSnapshot comparativeSnapshot =
        comparativeSnapshot(comparativeRange, postingCoverage);
    return new IncomeStatementView(
        bookIdentity,
        criteria.effectiveDateFrom(),
        criteria.effectiveDateTo(),
        comparativeRange,
        postingCoverage,
        currentSnapshot.sections(),
        currentSnapshot.netIncomeTotals(),
        comparativeSnapshot.sections(),
        comparativeSnapshot.netIncomeTotals());
  }

  private IncomeStatementSnapshot comparativeSnapshot(
      EffectiveDateRange comparativeRange, PostingCoverage postingCoverage) {
    return PeriodComparativeRangeSupport.boundedRange(comparativeRange)
        .map(range -> snapshot(context.bookStore().accountTotals(range, postingCoverage)))
        .orElseGet(IncomeStatementSnapshot::empty);
  }

  private IncomeStatementSnapshot snapshot(List<AccountCurrencyTotals> accountTotals) {
    IncomeStatementRows rows = new IncomeStatementRows();
    for (AccountCurrencyTotals accountTotal : accountTotals) {
      if (!context
          .accountingRules()
          .closePostingPolicy()
          .closesAccountType(accountTotal.account().accountType())) {
        continue;
      }
      rows.add(
          accountTotal.account().accountType(),
          ReportingRowViewFactory.incomeStatementRow(accountTotal));
    }
    List<CurrencyBalance> netIncomeTotals =
        profitAndLossContributionCalculator.contributionMap(accountTotals).entrySet().stream()
            .map(entry -> ReportingBalanceSupport.signedBalance(entry.getKey(), entry.getValue()))
            .sorted(ReportingRowOrdering.BALANCE_ORDER)
            .toList();
    return new IncomeStatementSnapshot(rows.sections(), netIncomeTotals);
  }

  /** Mutable row buckets grouped by profit-and-loss section before immutable projection. */
  private static final class IncomeStatementRows {
    private final Map<AccountType, List<IncomeStatementRowView>> rowsByType =
        Collections.synchronizedMap(new EnumMap<>(AccountType.class));

    private void add(AccountType accountType, IncomeStatementRowView row) {
      rowsByType.computeIfAbsent(accountType, ignored -> new ArrayList<>()).add(row);
    }

    private List<IncomeStatementSectionView> sections() {
      return SECTION_ORDER.stream()
          .map(
              accountType ->
                  ReportingRowViewFactory.toIncomeStatementSection(
                      accountType, rowsByType.getOrDefault(accountType, List.of())))
          .toList();
    }
  }

  private record IncomeStatementSnapshot(
      List<IncomeStatementSectionView> sections, List<CurrencyBalance> netIncomeTotals) {
    private static IncomeStatementSnapshot empty() {
      return new IncomeStatementSnapshot(List.of(), List.of());
    }

    private IncomeStatementSnapshot {
      sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
      netIncomeTotals = List.copyOf(Objects.requireNonNull(netIncomeTotals, "netIncomeTotals"));
    }
  }
}
