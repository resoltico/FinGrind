package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
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
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.BookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.policy.CoreBookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.policy.DerivedEquityLine;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Computes financial statements from the canonical posting stream and declared account registry.
 */
final class BookkeepingStatementService {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyUnit().code());
  static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      Comparator.comparing(FinancialPositionRowView::lineKind)
          .thenComparing(FinancialPositionRowView::lineClassification)
          .thenComparing(FinancialPositionRowView::lineCode)
          .thenComparing(row -> row.balance().netAmount().currencyUnit().code());
  static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      Comparator.comparing(IncomeStatementRowView::lineKind)
          .thenComparing(IncomeStatementRowView::lineClassification)
          .thenComparing(IncomeStatementRowView::lineCode)
          .thenComparing(row -> row.movement().netAmount().currencyUnit().code());
  static final Comparator<ChangesInEquityRowView> CHANGES_IN_EQUITY_ROW_ORDER =
      Comparator.comparing(ChangesInEquityRowView::lineKind)
          .thenComparing(ChangesInEquityRowView::lineClassification)
          .thenComparing(ChangesInEquityRowView::lineCode)
          .thenComparing(row -> row.closingBalance().netAmount().currencyUnit().code());

  private final BookLifecycleReader lifecycleReader;
  private final BookkeepingReportStore reportStore;
  private final BookkeepingPolicyPack policyPack;

  BookkeepingStatementService(
      BookLifecycleReader lifecycleReader, BookkeepingReportStore reportStore) {
    this(lifecycleReader, reportStore, CoreBookkeepingPolicyPack.current());
  }

  BookkeepingStatementService(
      BookLifecycleReader lifecycleReader,
      BookkeepingReportStore reportStore,
      BookkeepingPolicyPack policyPack) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
    this.policyPack = BookkeepingPolicyPack.requirePolicyPack(policyPack);
  }

  FinancialPositionView financialPosition(FinancialPositionCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    BookIdentity bookIdentity = bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.ALL_POSTING_KINDS;
    EffectiveDateRange comparativeRange =
        policyPack
            .statementComparativePolicy()
            .comparativeAsOf(bookIdentity, criteria.effectiveDateTo());
    List<FinancialPositionSectionView> sections =
        financialPositionSections(
            reportStore.accountTotals(
                criteria
                    .effectiveDateTo()
                    .<EffectiveDateRange>map(EffectiveDateRange::to)
                    .orElseGet(EffectiveDateRange::unbounded),
                postingCoverage));
    List<FinancialPositionSectionView> comparativeSections =
        comparativeRange.effectiveDateTo().isPresent()
            ? financialPositionSections(
                reportStore.accountTotals(
                    EffectiveDateRange.to(comparativeRange.effectiveDateTo().orElseThrow()),
                    postingCoverage))
            : List.of();
    return new FinancialPositionView(
        bookIdentity,
        criteria.effectiveDateTo(),
        comparativeRange,
        postingCoverage,
        sections,
        comparativeSections);
  }

  IncomeStatementView incomeStatement(IncomeStatementCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    BookIdentity bookIdentity = bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.NON_CLOSING_POSTINGS;
    EffectiveDateRange comparativeRange =
        policyPack
            .statementComparativePolicy()
            .comparativePeriod(
                bookIdentity, criteria.effectiveDateFrom(), criteria.effectiveDateTo());
    IncomeStatementSnapshot currentSnapshot =
        incomeStatementSnapshot(
            reportStore.accountTotals(
                EffectiveDateRange.of(criteria.effectiveDateFrom(), criteria.effectiveDateTo()),
                postingCoverage));
    IncomeStatementSnapshot comparativeSnapshot =
        incomeStatementSnapshot(reportStore.accountTotals(comparativeRange, postingCoverage));
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

  ChangesInEquityView changesInEquity(ChangesInEquityCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    BookIdentity bookIdentity = bookIdentity();
    PostingCoverage postingCoverage = PostingCoverage.ALL_POSTING_KINDS;
    EffectiveDateRange comparativeRange =
        policyPack
            .statementComparativePolicy()
            .comparativePeriod(
                bookIdentity, criteria.effectiveDateFrom(), criteria.effectiveDateTo());
    ChangesInEquitySnapshot currentSnapshot =
        changesInEquitySnapshot(
            criteria.effectiveDateFrom(), criteria.effectiveDateTo(), postingCoverage);
    ChangesInEquitySnapshot comparativeSnapshot =
        changesInEquitySnapshot(
            comparativeRange.effectiveDateFrom().orElseThrow(),
            comparativeRange.effectiveDateTo().orElseThrow(),
            postingCoverage);
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

  private IncomeStatementSnapshot incomeStatementSnapshot(
      List<AccountCurrencyTotals> accountTotals) {
    IncomeStatementRows rowsByType = new IncomeStatementRows();
    for (AccountCurrencyTotals accountTotal : accountTotals) {
      if (!policyPack.closePolicy().closesAccountType(accountTotal.account().accountType())) {
        continue;
      }
      rowsByType.add(accountTotal.account().accountType(), incomeStatementRow(accountTotal));
    }
    List<CurrencyBalance> netIncomeTotals =
        profitAndLossContributionMap(accountTotals).entrySet().stream()
            .map(entry -> signedBalance(entry.getKey(), entry.getValue()))
            .sorted(BALANCE_ORDER)
            .toList();
    return new IncomeStatementSnapshot(
        rowsByType.sections(List.of(AccountType.REVENUE, AccountType.EXPENSE)), netIncomeTotals);
  }

  private ChangesInEquitySnapshot changesInEquitySnapshot(
      LocalDate effectiveDateFrom, LocalDate effectiveDateTo, PostingCoverage postingCoverage) {
    BookIdentity bookIdentity = bookIdentity();
    LocalDate dayBefore = effectiveDateFrom.minusDays(1);
    List<AccountCurrencyTotals> openingTotals =
        reportStore.accountTotals(EffectiveDateRange.to(dayBefore), postingCoverage);
    List<AccountCurrencyTotals> movementTotals =
        reportStore.accountTotals(
            EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), postingCoverage);
    List<AccountCurrencyTotals> closingTotals =
        reportStore.accountTotals(EffectiveDateRange.to(effectiveDateTo), postingCoverage);
    Map<AccountCurrencyKey, AccountCurrencyTotals> openingTotalsByKey =
        indexAccountTotals(openingTotals);
    Map<AccountCurrencyKey, AccountCurrencyTotals> movementTotalsByKey =
        indexAccountTotals(movementTotals);
    Map<AccountCurrencyKey, AccountCurrencyTotals> closingTotalsByKey =
        indexAccountTotals(closingTotals);
    DerivedEquityLine currentPeriodResultLine =
        policyPack.statementPresentationPolicy().currentPeriodResultLine(bookIdentity);

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
              Optional.of(account.accountRole()),
              account.accountTaxonomy().financialPositionLineClassification().orElseThrow(),
              StatementLineKind.DECLARED_ACCOUNT,
              balanceOrZero(openingTotal, key.currencyUnit()),
              balanceOrZero(movementTotal, key.currencyUnit()),
              balanceOrZero(closingTotal, key.currencyUnit())));
    }

    Map<CurrencyUnit, Long> openingCurrentEarnings = profitAndLossContributionMap(openingTotals);
    Map<CurrencyUnit, Long> closingCurrentEarnings = profitAndLossContributionMap(closingTotals);
    currencyUnits(openingCurrentEarnings, closingCurrentEarnings)
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
                      currentPeriodResultLine.lineClassification(),
                      StatementLineKind.CURRENT_PERIOD_RESULT,
                      signedBalance(currencyUnit, opening),
                      signedBalance(currencyUnit, Math.subtractExact(closing, opening)),
                      signedBalance(currencyUnit, closing)));
            });

    rows.sort(CHANGES_IN_EQUITY_ROW_ORDER);
    return new ChangesInEquitySnapshot(
        rows,
        aggregateOpeningTotals(rows),
        aggregateMovementTotals(rows),
        aggregateClosingTotals(rows));
  }

  private BookIdentity bookIdentity() {
    return switch (lifecycleReader.inspectBook()) {
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized ->
          initialized.bookIdentity();
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException("Statement computation requires one initialized book.");
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Existing _ ->
          throw new IllegalStateException("Statement computation requires one initialized book.");
    };
  }

  private List<FinancialPositionSectionView> financialPositionSections(
      List<AccountCurrencyTotals> accountTotals) {
    DerivedEquityLine currentPeriodResultLine =
        policyPack.statementPresentationPolicy().currentPeriodResultLine(bookIdentity());
    FinancialPositionRows rowsByType = new FinancialPositionRows();
    for (AccountCurrencyTotals accountTotal : accountTotals) {
      if (!isFinancialPositionAccount(accountTotal.account().accountType())) {
        continue;
      }
      rowsByType.add(accountTotal.account().accountType(), financialPositionRow(accountTotal));
    }
    profitAndLossContributionMap(accountTotals)
        .forEach(
            (currencyUnit, signedMinorUnits) ->
                rowsByType.add(
                    AccountType.EQUITY,
                    currentEarningsFinancialPositionRow(
                        currentPeriodResultLine, currencyUnit, signedMinorUnits)));
    List<FinancialPositionSectionView> sections =
        rowsByType.sections(List.of(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY));
    assertAccountingEquation(sections);
    return sections;
  }

  private static FinancialPositionSectionView toFinancialPositionSection(
      AccountType accountType, List<FinancialPositionRowView> rows) {
    List<FinancialPositionRowView> orderedRows =
        rows.stream().sorted(FINANCIAL_POSITION_ROW_ORDER).toList();
    return new FinancialPositionSectionView(
        accountType,
        orderedRows,
        aggregateBalances(orderedRows.stream().map(FinancialPositionRowView::balance).toList()));
  }

  private static IncomeStatementSectionView toIncomeStatementSection(
      AccountType accountType, List<IncomeStatementRowView> rows) {
    List<IncomeStatementRowView> orderedRows =
        rows.stream().sorted(INCOME_STATEMENT_ROW_ORDER).toList();
    return new IncomeStatementSectionView(
        accountType,
        orderedRows,
        aggregateBalances(orderedRows.stream().map(IncomeStatementRowView::movement).toList()));
  }

  private static boolean isFinancialPositionAccount(AccountType accountType) {
    return switch (Objects.requireNonNull(accountType, "accountType")) {
      case ASSET, LIABILITY, EQUITY -> true;
      case REVENUE, EXPENSE -> false;
    };
  }

  private static FinancialPositionRowView financialPositionRow(AccountCurrencyTotals accountTotal) {
    return new FinancialPositionRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountType(),
        Optional.of(accountTotal.account().accountRole()),
        accountTotal
            .account()
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow(),
        StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  private static FinancialPositionRowView currentEarningsFinancialPositionRow(
      DerivedEquityLine currentPeriodResultLine, CurrencyUnit currencyUnit, long signedMinorUnits) {
    return new FinancialPositionRowView(
        currentPeriodResultLine.lineCode(),
        currentPeriodResultLine.lineName(),
        AccountType.EQUITY,
        Optional.empty(),
        currentPeriodResultLine.lineClassification(),
        StatementLineKind.CURRENT_PERIOD_RESULT,
        signedBalance(currencyUnit, signedMinorUnits));
  }

  private static IncomeStatementRowView incomeStatementRow(AccountCurrencyTotals accountTotal) {
    return new IncomeStatementRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountType(),
        Optional.of(accountTotal.account().accountRole()),
        accountTotal.account().accountTaxonomy().profitAndLossLineClassification().orElseThrow(),
        StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  private Map<CurrencyUnit, Long> profitAndLossContributionMap(
      List<AccountCurrencyTotals> accountTotals) {
    CurrencyContributionAccumulator contributions = new CurrencyContributionAccumulator();
    for (AccountCurrencyTotals accountTotal : accountTotals) {
      RegisteredAccount account = accountTotal.account();
      if (!policyPack.closePolicy().closesAccountType(account.accountType())) {
        continue;
      }
      CurrencyBalance balance = accountTotal.balance();
      if (balance.balanceSide() == BalanceSide.ZERO) {
        continue;
      }
      contributions.record(
          accountTotal.currencyUnit(),
          AccountSemantics.profitAndLossContributionMinorUnits(
              account.accountType(),
              account.accountRole(),
              balance.balanceSide(),
              balance.netAmount().minorUnits()));
    }
    return contributions.snapshot();
  }

  private static CurrencyBalance signedBalance(CurrencyUnit currencyUnit, long signedMinorUnits) {
    return signedMinorUnits >= 0L
        ? BalanceMath.currencyBalance(currencyUnit, 0L, signedMinorUnits)
        : BalanceMath.currencyBalance(currencyUnit, Math.absExact(signedMinorUnits), 0L);
  }

  private static List<CurrencyBalance> aggregateOpeningTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::openingBalance).toList());
  }

  private static List<CurrencyBalance> aggregateMovementTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::movement).toList());
  }

  private static List<CurrencyBalance> aggregateClosingTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::closingBalance).toList());
  }

  private static List<CurrencyBalance> aggregateBalances(List<CurrencyBalance> balances) {
    AccountBalanceAccumulator totals = new AccountBalanceAccumulator();
    for (CurrencyBalance balance : balances) {
      totals.record(balance);
    }
    return totals.balances();
  }

  @SafeVarargs
  private static List<CurrencyUnit> currencyUnits(Map<CurrencyUnit, ?>... maps) {
    SortedSet<CurrencyUnit> ordered = new TreeSet<>(Comparator.comparing(CurrencyUnit::code));
    for (Map<CurrencyUnit, ?> map : maps) {
      ordered.addAll(map.keySet());
    }
    return List.copyOf(ordered);
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
    Map<AccountCurrencyKey, AccountCurrencyTotals> indexed = new ConcurrentHashMap<>();
    accountTotals.forEach(
        accountTotal ->
            indexed.put(
                new AccountCurrencyKey(
                    accountTotal.account().accountCode(), accountTotal.currencyUnit()),
                accountTotal));
    return Map.copyOf(indexed);
  }

  private static CurrencyBalance balanceOrZero(
      @Nullable AccountCurrencyTotals accountTotal, CurrencyUnit currencyUnit) {
    return accountTotal == null
        ? BalanceMath.currencyBalance(currencyUnit, 0L, 0L)
        : accountTotal.balance();
  }

  static void assertAccountingEquation(List<FinancialPositionSectionView> sections) {
    Map<CurrencyUnit, Long> assetTotals = signedSectionTotals(section(sections, AccountType.ASSET));
    Map<CurrencyUnit, Long> liabilityTotals =
        signedSectionTotals(section(sections, AccountType.LIABILITY));
    Map<CurrencyUnit, Long> equityTotals =
        signedSectionTotals(section(sections, AccountType.EQUITY));
    for (CurrencyUnit currencyUnit : currencyUnits(assetTotals, liabilityTotals, equityTotals)) {
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
    Map<CurrencyUnit, Long> totals = new ConcurrentHashMap<>();
    section
        .totals()
        .forEach(
            balance -> totals.put(balance.netAmount().currencyUnit(), signedMinorUnits(balance)));
    return Map.copyOf(totals);
  }

  static long signedMinorUnits(CurrencyBalance balance) {
    return switch (balance.balanceSide()) {
      case DEBIT -> balance.netAmount().minorUnits();
      case CREDIT -> Math.negateExact(balance.netAmount().minorUnits());
      case ZERO -> 0L;
    };
  }

  private record SignedDebitCreditTotals(long debitTotalMinor, long creditTotalMinor) {
    private static final SignedDebitCreditTotals ZERO = new SignedDebitCreditTotals(0L, 0L);

    SignedDebitCreditTotals plus(long debitMinor, long creditMinor) {
      return new SignedDebitCreditTotals(
          Math.addExact(debitTotalMinor, debitMinor), Math.addExact(creditTotalMinor, creditMinor));
    }

    CurrencyBalance balance(CurrencyUnit currencyUnit) {
      return BalanceMath.currencyBalance(currencyUnit, debitTotalMinor, creditTotalMinor);
    }
  }

  /** Groups financial-position rows by section account type before final rendering. */
  private static final class FinancialPositionRows {
    private final Map<AccountType, List<FinancialPositionRowView>> rowsByType =
        new ConcurrentHashMap<>();

    void add(AccountType accountType, FinancialPositionRowView row) {
      rowsByType.computeIfAbsent(accountType, ignored -> new ArrayList<>()).add(row);
    }

    List<FinancialPositionSectionView> sections(List<AccountType> sectionOrder) {
      return sectionOrder.stream()
          .map(accountType -> toFinancialPositionSection(accountType, rows(accountType)))
          .toList();
    }

    private List<FinancialPositionRowView> rows(AccountType accountType) {
      return rowsByType.getOrDefault(accountType, List.of());
    }
  }

  /** Groups income-statement rows by section account type before final rendering. */
  private static final class IncomeStatementRows {
    private final Map<AccountType, List<IncomeStatementRowView>> rowsByType =
        new ConcurrentHashMap<>();

    void add(AccountType accountType, IncomeStatementRowView row) {
      rowsByType.computeIfAbsent(accountType, ignored -> new ArrayList<>()).add(row);
    }

    List<IncomeStatementSectionView> sections(List<AccountType> sectionOrder) {
      return sectionOrder.stream()
          .map(accountType -> toIncomeStatementSection(accountType, rows(accountType)))
          .toList();
    }

    private List<IncomeStatementRowView> rows(AccountType accountType) {
      return rowsByType.getOrDefault(accountType, List.of());
    }
  }

  /** Accumulates exact debit/credit totals per currency before projecting balances. */
  private static final class AccountBalanceAccumulator {
    private final Map<CurrencyUnit, SignedDebitCreditTotals> totalsByCurrency =
        new ConcurrentHashMap<>();

    void record(CurrencyBalance balance) {
      totalsByCurrency.compute(
          balance.netAmount().currencyUnit(),
          (ignored, existing) ->
              (existing == null ? SignedDebitCreditTotals.ZERO : existing)
                  .plus(balance.debitTotal().minorUnits(), balance.creditTotal().minorUnits()));
    }

    List<CurrencyBalance> balances() {
      return snapshot().entrySet().stream()
          .map(entry -> entry.getValue().balance(entry.getKey()))
          .sorted(BALANCE_ORDER)
          .toList();
    }

    Map<CurrencyUnit, SignedDebitCreditTotals> snapshot() {
      return Map.copyOf(totalsByCurrency);
    }
  }

  /** Accumulates signed profit-and-loss contributions per currency. */
  private static final class CurrencyContributionAccumulator {
    private final Map<CurrencyUnit, Long> contributions = new ConcurrentHashMap<>();

    void record(CurrencyUnit currencyUnit, long signedContribution) {
      contributions.merge(currencyUnit, signedContribution, Math::addExact);
    }

    Map<CurrencyUnit, Long> snapshot() {
      return Map.copyOf(contributions);
    }
  }

  /** Stable composite key for one declared account and one currency bucket. */
  private record AccountCurrencyKey(
      dev.erst.fingrind.core.AccountCode accountCode, CurrencyUnit currencyUnit) {
    private AccountCurrencyKey {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }
  }

  private record IncomeStatementSnapshot(
      List<IncomeStatementSectionView> sections, List<CurrencyBalance> netIncomeTotals) {
    private IncomeStatementSnapshot {
      sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
      netIncomeTotals = List.copyOf(Objects.requireNonNull(netIncomeTotals, "netIncomeTotals"));
    }
  }

  private record ChangesInEquitySnapshot(
      List<ChangesInEquityRowView> rows,
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals) {
    private ChangesInEquitySnapshot {
      rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
      openingTotals = List.copyOf(Objects.requireNonNull(openingTotals, "openingTotals"));
      movementTotals = List.copyOf(Objects.requireNonNull(movementTotals, "movementTotals"));
      closingTotals = List.copyOf(Objects.requireNonNull(closingTotals, "closingTotals"));
    }
  }
}
