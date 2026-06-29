package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.CashFlowSectionView;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementView;
import dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolver;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Computes one statement of cash receipts and payments from cash-account posting movement. */
final class CashFlowStatementCalculator {
  private final ReportingContext context;

  CashFlowStatementCalculator(ReportingContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  CashFlowStatementView view(CashFlowStatementCriteria criteria) {
    BookIdentity bookIdentity = context.bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.NON_CLOSING_POSTINGS;
    EffectiveDateRange comparativeRange =
        ComparativeRangeResolver.period(
            bookIdentity,
            criteria.effectiveDateFrom(),
            criteria.effectiveDateTo(),
            criteria.comparativeSelection(),
            context.accountingRules().statementComparativePolicy());
    CashFlowSnapshot currentSnapshot =
        snapshot(criteria.effectiveDateFrom(), criteria.effectiveDateTo(), postingCoverage);
    CashFlowSnapshot comparativeSnapshot = comparativeSnapshot(comparativeRange, postingCoverage);
    return new CashFlowStatementView(
        bookIdentity,
        criteria.effectiveDateFrom(),
        criteria.effectiveDateTo(),
        comparativeRange,
        postingCoverage,
        currentSnapshot.openingCashTotals(),
        currentSnapshot.sections(),
        currentSnapshot.movementTotals(),
        currentSnapshot.closingCashTotals(),
        comparativeSnapshot.openingCashTotals(),
        comparativeSnapshot.sections(),
        comparativeSnapshot.movementTotals(),
        comparativeSnapshot.closingCashTotals());
  }

  private CashFlowSnapshot comparativeSnapshot(
      EffectiveDateRange comparativeRange, PostingCoverage postingCoverage) {
    return PeriodComparativeRangeSupport.boundedRange(comparativeRange)
        .map(
            range ->
                snapshot(
                    range.effectiveDateFrom().orElseThrow(),
                    range.effectiveDateTo().orElseThrow(),
                    postingCoverage))
        .orElseGet(CashFlowSnapshot::empty);
  }

  private CashFlowSnapshot snapshot(
      LocalDate effectiveDateFrom, LocalDate effectiveDateTo, PostingCoverage postingCoverage) {
    List<CurrencyBalance> openingCashTotals =
        cashTotals(EffectiveDateRange.to(effectiveDateFrom.minusDays(1)), postingCoverage);
    List<CashFlowSectionView> sections =
        sections(EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), postingCoverage);
    List<CurrencyBalance> movementTotals =
        ReportingBalanceSupport.aggregateBalances(
            sections.stream().flatMap(section -> section.totals().stream()).toList());
    List<CurrencyBalance> closingCashTotals =
        cashTotals(EffectiveDateRange.to(effectiveDateTo), postingCoverage);
    assertCashArticulation(openingCashTotals, movementTotals, closingCashTotals);
    return new CashFlowSnapshot(openingCashTotals, sections, movementTotals, closingCashTotals);
  }

  private List<CurrencyBalance> cashTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    return ReportingBalanceSupport.aggregateBalances(
        context.bookStore().accountTotals(effectiveDateRange, postingCoverage).stream()
            .filter(accountTotal -> accountTotal.account().cashAndCashEquivalent())
            .map(AccountCurrencyTotals::balance)
            .map(CashFlowStatementCalculator::netBalance)
            .toList());
  }

  private static CurrencyBalance netBalance(CurrencyBalance balance) {
    CurrencyUnit currencyUnit = balance.netAmount().currencyUnit();
    long netMinor = balance.netAmount().minorUnits();
    return switch (balance.balanceSide()) {
      case DEBIT -> BalanceMath.currencyBalance(currencyUnit, netMinor, 0L);
      case CREDIT -> BalanceMath.currencyBalance(currencyUnit, 0L, netMinor);
      case ZERO -> BalanceMath.currencyBalance(currencyUnit, 0L, 0L);
    };
  }

  private List<CashFlowSectionView> sections(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    Map<AccountCode, RegisteredAccount> accountsByCode =
        context.bookStore().allAccounts().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, Function.identity(), (left, right) -> left));
    CashFlowSectionAccumulator rows = new CashFlowSectionAccumulator();
    context.bookStore().postings(effectiveDateRange).stream()
        .filter(posting -> postingCoverage.includes(posting.postingKind()))
        .flatMap(
            posting ->
                CashFlowPostingMovementClassifier.postingMovements(accountsByCode, posting)
                    .stream())
        .forEach(rows::add);
    return rows.sections();
  }

  private static void assertCashArticulation(
      List<CurrencyBalance> openingCashTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingCashTotals) {
    Map<CurrencyUnit, Long> openingByCurrency = signedTotals(openingCashTotals);
    Map<CurrencyUnit, Long> movementByCurrency = signedTotals(movementTotals);
    Map<CurrencyUnit, Long> closingByCurrency = signedTotals(closingCashTotals);
    for (CurrencyUnit currencyUnit :
        ReportingBalanceSupport.currencyUnits(
            openingByCurrency, movementByCurrency, closingByCurrency)) {
      long expectedClosing =
          Math.addExact(
              openingByCurrency.getOrDefault(currencyUnit, 0L),
              movementByCurrency.getOrDefault(currencyUnit, 0L));
      long actualClosing = closingByCurrency.getOrDefault(currencyUnit, 0L);
      if (expectedClosing != actualClosing) {
        throw new IllegalStateException(
            "Cash-flow articulation failed for currency "
                + currencyUnit.code()
                + ": opening cash plus movement does not equal closing cash.");
      }
    }
  }

  private static Map<CurrencyUnit, Long> signedTotals(List<CurrencyBalance> balances) {
    return Map.copyOf(
        balances.stream()
            .collect(
                Collectors.toConcurrentMap(
                    balance -> balance.netAmount().currencyUnit(),
                    ReportingBalanceSupport::signedMinorUnits,
                    Math::addExact)));
  }

  private record CashFlowSnapshot(
      List<CurrencyBalance> openingCashTotals,
      List<CashFlowSectionView> sections,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingCashTotals) {
    private static CashFlowSnapshot empty() {
      return new CashFlowSnapshot(List.of(), List.of(), List.of(), List.of());
    }

    private CashFlowSnapshot {
      openingCashTotals =
          List.copyOf(Objects.requireNonNull(openingCashTotals, "openingCashTotals"));
      sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
      movementTotals = List.copyOf(Objects.requireNonNull(movementTotals, "movementTotals"));
      closingCashTotals =
          List.copyOf(Objects.requireNonNull(closingCashTotals, "closingCashTotals"));
    }
  }
}
