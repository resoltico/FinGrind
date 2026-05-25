package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.policy.DerivedEquityLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes statement-of-financial-position views from the canonical account totals. */
final class FinancialPositionStatementCalculator {
  private static final List<AccountType> SECTION_ORDER =
      List.of(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY);

  private final BookkeepingStatementContext context;
  private final ProfitAndLossContributionCalculator profitAndLossContributionCalculator;

  FinancialPositionStatementCalculator(BookkeepingStatementContext context) {
    this.context = Objects.requireNonNull(context, "context");
    this.profitAndLossContributionCalculator =
        new ProfitAndLossContributionCalculator(context::accountingRules);
  }

  FinancialPositionView view(FinancialPositionCriteria criteria) {
    BookIdentity bookIdentity = context.bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.ALL_POSTING_KINDS;
    EffectiveDateRange comparativeRange =
        context
            .accountingRules()
            .statementComparativePolicy()
            .comparativeAsOf(bookIdentity, criteria.effectiveDateAsOf());
    List<FinancialPositionSectionView> sections =
        sections(
            bookIdentity,
            context
                .reportStore()
                .accountTotals(
                    criteria
                        .effectiveDateAsOf()
                        .<EffectiveDateRange>map(EffectiveDateRange::to)
                        .orElseGet(EffectiveDateRange::unbounded),
                    postingCoverage));
    List<FinancialPositionSectionView> comparativeSections =
        comparativeRange.effectiveDateTo().isPresent()
            ? sections(
                bookIdentity,
                context
                    .reportStore()
                    .accountTotals(
                        EffectiveDateRange.to(comparativeRange.effectiveDateTo().orElseThrow()),
                        postingCoverage))
            : List.of();
    return new FinancialPositionView(
        bookIdentity,
        criteria.effectiveDateAsOf(),
        comparativeRange,
        postingCoverage,
        sections,
        comparativeSections);
  }

  private List<FinancialPositionSectionView> sections(
      BookIdentity bookIdentity, List<AccountCurrencyTotals> accountTotals) {
    DerivedEquityLine currentPeriodResultLine =
        context
            .accountingRules()
            .statementPresentationPolicy()
            .currentPeriodResultLine(bookIdentity);
    FinancialPositionRows rows = new FinancialPositionRows();
    for (AccountCurrencyTotals accountTotal : accountTotals) {
      if (!isFinancialPositionAccount(accountTotal.account().accountType())) {
        continue;
      }
      rows.add(
          accountTotal.account().accountType(),
          BookkeepingStatementViewSupport.financialPositionRow(accountTotal));
    }
    profitAndLossContributionCalculator
        .contributionMap(accountTotals)
        .forEach(
            (currencyUnit, signedMinorUnits) ->
                rows.add(
                    AccountType.EQUITY,
                    BookkeepingStatementViewSupport.currentEarningsFinancialPositionRow(
                        currentPeriodResultLine, currencyUnit, signedMinorUnits)));
    List<FinancialPositionSectionView> sections = rows.sections();
    BookkeepingStatementViewSupport.assertAccountingEquation(sections);
    return sections;
  }

  private static boolean isFinancialPositionAccount(AccountType accountType) {
    return switch (Objects.requireNonNull(accountType, "accountType")) {
      case ASSET, LIABILITY, EQUITY -> true;
      case REVENUE, EXPENSE -> false;
    };
  }

  /** Mutable row buckets grouped by financial-position section before immutable projection. */
  private static final class FinancialPositionRows {
    private final Map<AccountType, List<FinancialPositionRowView>> rowsByType =
        Collections.synchronizedMap(new EnumMap<>(AccountType.class));

    private void add(AccountType accountType, FinancialPositionRowView row) {
      rowsByType.computeIfAbsent(accountType, ignored -> new ArrayList<>()).add(row);
    }

    private List<FinancialPositionSectionView> sections() {
      return SECTION_ORDER.stream()
          .map(
              accountType ->
                  BookkeepingStatementViewSupport.toFinancialPositionSection(
                      accountType, rowsByType.getOrDefault(accountType, List.of())))
          .toList();
    }
  }
}
