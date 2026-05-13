package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Computes financial statements from the canonical posting stream and declared account registry.
 */
final class BookkeepingStatementService {
  private static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyUnit().code());
  private static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      Comparator.comparing(FinancialPositionRowView::synthetic)
          .thenComparing(FinancialPositionRowView::lineCode)
          .thenComparing(row -> row.balance().netAmount().currencyUnit().code());
  private static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      Comparator.comparing(IncomeStatementRowView::synthetic)
          .thenComparing(IncomeStatementRowView::lineCode)
          .thenComparing(row -> row.movement().netAmount().currencyUnit().code());
  private static final Comparator<ChangesInEquityRowView> CHANGES_IN_EQUITY_ROW_ORDER =
      Comparator.comparing(ChangesInEquityRowView::synthetic)
          .thenComparing(ChangesInEquityRowView::lineCode)
          .thenComparing(row -> row.closingBalance().netAmount().currencyUnit().code());

  private final BookStore bookStore;

  BookkeepingStatementService(BookStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  FinancialPositionView financialPosition(FinancialPositionCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    EffectiveDateRange range = EffectiveDateRange.of(null, criteria.effectiveDateTo().orElse(null));
    List<CommittedPosting> postings = bookStore.postings(range);
    List<RegisteredAccount> accounts = bookStore.allAccounts();
    FinancialPositionRows rowsByType = new FinancialPositionRows();
    for (RegisteredAccount account : accounts) {
      if (!isFinancialPositionAccount(account.accountType())) {
        continue;
      }
      balanceMapForAccount(postings, account)
          .forEach(
              (currencyUnit, totals) ->
                  rowsByType.add(
                      account.accountType(), financialPositionRow(account, currencyUnit, totals)));
    }
    profitAndLossContributionMap(postings)
        .forEach(
            (currencyUnit, signedMinorUnits) ->
                rowsByType.add(
                    AccountType.EQUITY,
                    currentEarningsFinancialPositionRow(currencyUnit, signedMinorUnits)));
    return new FinancialPositionView(
        criteria.effectiveDateTo(),
        rowsByType.sections(List.of(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY)));
  }

  IncomeStatementView incomeStatement(IncomeStatementCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    EffectiveDateRange range =
        EffectiveDateRange.of(criteria.effectiveDateFrom(), criteria.effectiveDateTo());
    List<CommittedPosting> postings = standardPostings(bookStore.postings(range));
    List<RegisteredAccount> accounts = bookStore.allAccounts();
    IncomeStatementRows rowsByType = new IncomeStatementRows();
    for (RegisteredAccount account : accounts) {
      if (!AccountSemantics.closesIntoRetainedEarnings(account.accountType())) {
        continue;
      }
      balanceMapForAccount(postings, account)
          .forEach(
              (currencyUnit, totals) ->
                  rowsByType.add(
                      account.accountType(), incomeStatementRow(account, currencyUnit, totals)));
    }
    List<CurrencyBalance> netIncomeTotals =
        profitAndLossContributionMap(postings).entrySet().stream()
            .map(entry -> signedBalance(entry.getKey(), entry.getValue()))
            .sorted(BALANCE_ORDER)
            .toList();
    return new IncomeStatementView(
        criteria.effectiveDateFrom(),
        criteria.effectiveDateTo(),
        rowsByType.sections(List.of(AccountType.REVENUE, AccountType.EXPENSE)),
        netIncomeTotals);
  }

  ChangesInEquityView changesInEquity(ChangesInEquityCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria");
    LocalDate dayBefore = criteria.effectiveDateFrom().minusDays(1);
    List<RegisteredAccount> accounts = bookStore.allAccounts();
    List<CommittedPosting> openingPostings =
        bookStore.postings(EffectiveDateRange.of(null, dayBefore));
    List<CommittedPosting> periodPostings =
        bookStore.postings(
            EffectiveDateRange.of(criteria.effectiveDateFrom(), criteria.effectiveDateTo()));
    List<CommittedPosting> closingPostings =
        bookStore.postings(EffectiveDateRange.of(null, criteria.effectiveDateTo()));

    List<ChangesInEquityRowView> rows = new ArrayList<>();
    for (RegisteredAccount account : accounts) {
      if (account.accountType() != AccountType.EQUITY) {
        continue;
      }
      Map<CurrencyUnit, SignedDebitCreditTotals> openingTotals =
          balanceMapForAccount(openingPostings, account);
      Map<CurrencyUnit, SignedDebitCreditTotals> movementTotals =
          balanceMapForAccount(periodPostings, account);
      Map<CurrencyUnit, SignedDebitCreditTotals> closingTotals =
          balanceMapForAccount(closingPostings, account);
      currencyUnits(openingTotals, movementTotals, closingTotals)
          .forEach(
              currencyUnit ->
                  rows.add(
                      new ChangesInEquityRowView(
                          account.accountCode().value(),
                          account.accountName().value(),
                          false,
                          openingTotals
                              .getOrDefault(currencyUnit, SignedDebitCreditTotals.ZERO)
                              .balance(currencyUnit),
                          movementTotals
                              .getOrDefault(currencyUnit, SignedDebitCreditTotals.ZERO)
                              .balance(currencyUnit),
                          closingTotals
                              .getOrDefault(currencyUnit, SignedDebitCreditTotals.ZERO)
                              .balance(currencyUnit))));
    }

    Map<CurrencyUnit, Long> openingCurrentEarnings = profitAndLossContributionMap(openingPostings);
    Map<CurrencyUnit, Long> closingCurrentEarnings = profitAndLossContributionMap(closingPostings);
    currencyUnits(openingCurrentEarnings, closingCurrentEarnings)
        .forEach(
            currencyUnit -> {
              long opening = openingCurrentEarnings.getOrDefault(currencyUnit, 0L);
              long closing = closingCurrentEarnings.getOrDefault(currencyUnit, 0L);
              rows.add(
                  new ChangesInEquityRowView(
                      "current-earnings",
                      "Current Earnings",
                      true,
                      signedBalance(currencyUnit, opening),
                      signedBalance(currencyUnit, Math.subtractExact(closing, opening)),
                      signedBalance(currencyUnit, closing)));
            });

    rows.sort(CHANGES_IN_EQUITY_ROW_ORDER);
    return new ChangesInEquityView(
        criteria.effectiveDateFrom(),
        criteria.effectiveDateTo(),
        rows,
        aggregateOpeningTotals(rows),
        aggregateMovementTotals(rows),
        aggregateClosingTotals(rows));
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

  private static List<CommittedPosting> standardPostings(List<CommittedPosting> postings) {
    return postings.stream().filter(posting -> posting.postingKind().isStandard()).toList();
  }

  private static FinancialPositionRowView financialPositionRow(
      RegisteredAccount account, CurrencyUnit currencyUnit, SignedDebitCreditTotals totals) {
    return new FinancialPositionRowView(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType(),
        false,
        totals.balance(currencyUnit));
  }

  private static FinancialPositionRowView currentEarningsFinancialPositionRow(
      CurrencyUnit currencyUnit, long signedMinorUnits) {
    return new FinancialPositionRowView(
        "current-earnings",
        "Current Earnings",
        AccountType.EQUITY,
        true,
        signedBalance(currencyUnit, signedMinorUnits));
  }

  private static IncomeStatementRowView incomeStatementRow(
      RegisteredAccount account, CurrencyUnit currencyUnit, SignedDebitCreditTotals totals) {
    return new IncomeStatementRowView(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType(),
        false,
        totals.balance(currencyUnit));
  }

  private static Map<CurrencyUnit, SignedDebitCreditTotals> balanceMapForAccount(
      List<CommittedPosting> postings, RegisteredAccount account) {
    AccountBalanceAccumulator totalsByCurrency = new AccountBalanceAccumulator();
    for (CommittedPosting posting : postings) {
      for (JournalLine line : posting.journalEntry().lines()) {
        if (!line.accountCode().equals(account.accountCode())) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }
    return totalsByCurrency.snapshot();
  }

  private Map<CurrencyUnit, Long> profitAndLossContributionMap(List<CommittedPosting> postings) {
    AccountIndex accountsByCode = AccountIndex.of(bookStore.allAccounts());
    return profitAndLossContributionMap(postings, accountsByCode);
  }

  private static Map<CurrencyUnit, Long> profitAndLossContributionMap(
      List<CommittedPosting> postings, AccountIndex accountsByCode) {
    CurrencyContributionAccumulator contributions = new CurrencyContributionAccumulator();
    for (CommittedPosting posting : postings) {
      for (JournalLine line : posting.journalEntry().lines()) {
        @Nullable RegisteredAccount account = accountsByCode.find(line.accountCode());
        if (account == null
            || !AccountSemantics.closesIntoRetainedEarnings(account.accountType())) {
          continue;
        }
        long signedContribution =
            AccountSemantics.profitAndLossContributionMinorUnits(
                account.accountType(),
                account.accountRole(),
                line.side() == JournalLine.EntrySide.DEBIT ? BalanceSide.DEBIT : BalanceSide.CREDIT,
                line.amount().minorUnits());
        contributions.record(line.amount().currencyUnit(), signedContribution);
      }
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

  private record SignedDebitCreditTotals(long debitTotalMinor, long creditTotalMinor) {
    private static final SignedDebitCreditTotals ZERO = new SignedDebitCreditTotals(0L, 0L);

    SignedDebitCreditTotals plus(JournalLine.EntrySide side, long amountMinor) {
      return switch (Objects.requireNonNull(side, "side")) {
        case DEBIT ->
            new SignedDebitCreditTotals(
                Math.addExact(debitTotalMinor, amountMinor), creditTotalMinor);
        case CREDIT ->
            new SignedDebitCreditTotals(
                debitTotalMinor, Math.addExact(creditTotalMinor, amountMinor));
      };
    }

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

    void record(JournalLine line) {
      totalsByCurrency.compute(
          line.amount().currencyUnit(),
          (ignored, existing) ->
              (existing == null ? SignedDebitCreditTotals.ZERO : existing)
                  .plus(line.side(), line.amount().minorUnits()));
    }

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

  /** Indexes declared accounts by account code for statement derivation. */
  private static final class AccountIndex {
    private final Map<String, RegisteredAccount> accountsByCode = new ConcurrentHashMap<>();

    static AccountIndex of(List<RegisteredAccount> accounts) {
      AccountIndex index = new AccountIndex();
      accounts.forEach(account -> index.accountsByCode.put(account.accountCode().value(), account));
      return index;
    }

    @Nullable RegisteredAccount find(dev.erst.fingrind.core.AccountCode accountCode) {
      return accountsByCode.get(accountCode.value());
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
}
