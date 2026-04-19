package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Exact report-oriented SQLite reads for office-worker reporting workflows. */
final class SqliteReportReadSupport {
  private static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyCode().value());

  private final SqlitePostingReadSupport postingReadSupport;

  SqliteReportReadSupport(SqlitePostingReadSupport postingReadSupport) {
    this.postingReadSupport = Objects.requireNonNull(postingReadSupport, "postingReadSupport");
  }

  TrialBalanceReport trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceQuery query)
      throws SqliteNativeException {
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    Map<AccountCode, AccountTotals> totalsByAccount = insertionOrderedMap();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadTrialBalanceLines(filterEffectiveDateTo))) {
      if (filterEffectiveDateTo) {
        statement.bindText(1, query.effectiveDateTo().orElseThrow().toString());
      }
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        DeclaredAccount account = SqlitePostingMapper.declaredAccount(statement);
        CurrencyCode currencyCode = reportCurrencyCode(statement);
        BigDecimal amount = reportAmount(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        AccountTotals accountTotals = accountTotalsFor(totalsByAccount, account);
        accountTotals.add(currencyCode, entrySide, amount);
      }
    }
    List<TrialBalanceRow> rows = new ArrayList<>();
    totalsByAccount
        .values()
        .forEach(accountTotals -> rows.addAll(accountTotals.trialBalanceRows()));
    return new TrialBalanceReport(query.effectiveDateTo(), rows);
  }

  PeriodSummaryReport periodSummary(SqliteNativeDatabase activeDatabase, PeriodSummaryQuery query)
      throws SqliteNativeException {
    Map<AccountCode, AccountTotals> accountActivity = insertionOrderedMap();
    Map<CurrencyCode, Totals> currencyTotals = insertionOrderedMap();
    Set<String> postingIds = insertionOrderedSet();
    int postingLineCount = 0;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadPeriodSummaryLines())) {
      statement.bindText(1, query.effectiveDateFrom().toString());
      statement.bindText(2, query.effectiveDateTo().toString());
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        postingIds.add(
            SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_POSTING_ID));
        postingLineCount++;
        DeclaredAccount account = SqlitePostingMapper.declaredAccount(statement);
        CurrencyCode currencyCode = reportCurrencyCode(statement);
        BigDecimal amount = reportAmount(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        accountTotalsFor(accountActivity, account).add(currencyCode, entrySide, amount);
        totalsFor(currencyTotals, currencyCode).add(entrySide, amount);
      }
    }
    List<PeriodCurrencySummary> currencySummaryRows =
        currencyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(CurrencyCode::value)))
            .map(
                entry ->
                    new PeriodCurrencySummary(
                        SqliteBalanceMath.currencyBalance(
                            entry.getKey(),
                            entry.getValue().debit,
                            entry.getValue().credit,
                            NormalBalance.DEBIT)))
            .toList();
    List<PeriodAccountActivityRow> activityRows =
        accountActivity.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(AccountCode::value)))
            .flatMap(entry -> entry.getValue().periodActivityRows().stream())
            .toList();
    return new PeriodSummaryReport(
        query.effectiveDateFrom(),
        query.effectiveDateTo(),
        postingIds.size(),
        postingLineCount,
        accountActivity.size(),
        currencySummaryRows,
        activityRows);
  }

  AccountLedgerReport accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    List<CurrencyBalance> openingBalances = openingBalances(activeDatabase, query, account);
    Map<CurrencyCode, BigDecimal> runningTotals = signedRunningTotals(openingBalances);
    List<AccountLedgerEntry> entries = new ArrayList<>();
    for (PostingFact postingFact : postingFactsForAccountLedger(activeDatabase, query)) {
      LedgerMovement movement = ledgerMovement(postingFact, account);
      BigDecimal signedNet = movement.debit.subtract(movement.credit);
      BigDecimal runningSigned =
          runningTotals.merge(movement.currencyCode, signedNet, BigDecimal::add);
      entries.add(
          new AccountLedgerEntry(
              postingFact,
              SqliteBalanceMath.currencyBalance(
                  movement.currencyCode, movement.debit, movement.credit, account.normalBalance()),
              new Money(movement.currencyCode, runningSigned.abs()),
              runningBalanceSide(runningSigned)));
    }
    List<CurrencyBalance> closingBalances = closingBalances(activeDatabase, query, account);
    return new AccountLedgerReport(
        account, query.effectiveDateRange(), openingBalances, entries, closingBalances);
  }

  private List<CurrencyBalance> openingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    Optional<LocalDate> effectiveDateFrom = query.effectiveDateFrom();
    if (effectiveDateFrom.isEmpty()) {
      return List.of();
    }
    LocalDate lowerBound = effectiveDateFrom.orElseThrow();
    if (lowerBound.equals(LocalDate.MIN)) {
      return List.of();
    }
    return postingReadSupport
        .accountBalance(
            activeDatabase,
            new AccountBalanceQuery(
                account.accountCode(), Optional.empty(), Optional.of(priorDate(lowerBound))),
            account)
        .balances();
  }

  private List<CurrencyBalance> closingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    return postingReadSupport
        .accountBalance(
            activeDatabase,
            new AccountBalanceQuery(
                account.accountCode(), Optional.empty(), query.effectiveDateTo()),
            account)
        .balances();
  }

  private List<PostingFact> postingFactsForAccountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query) throws SqliteNativeException {
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    return postingReadSupport.loadPostingFacts(
        activeDatabase,
        SqlitePostingSql.listPostingsForAccountLedger(
            filterEffectiveDateFrom, filterEffectiveDateTo),
        statement -> {
          int bindIndex = 1;
          statement.bindText(bindIndex, query.accountCode().value());
          bindIndex++;
          if (filterEffectiveDateFrom) {
            statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
            bindIndex++;
          }
          if (filterEffectiveDateTo) {
            statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
          }
        });
  }

  private static LedgerMovement ledgerMovement(PostingFact postingFact, DeclaredAccount account) {
    List<JournalLine> matchingLines =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.accountCode().equals(account.accountCode()))
            .toList();
    CurrencyCode currencyCode = matchingLines.getFirst().amount().currencyCode();
    BigDecimal debit = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;
    for (JournalLine line : matchingLines) {
      if (line.side() == JournalLine.EntrySide.DEBIT) {
        debit = debit.add(line.amount().amount());
      } else {
        credit = credit.add(line.amount().amount());
      }
    }
    return new LedgerMovement(currencyCode, debit, credit);
  }

  private static Map<CurrencyCode, BigDecimal> signedRunningTotals(
      List<CurrencyBalance> openingBalances) {
    Map<CurrencyCode, BigDecimal> runningTotals = insertionOrderedMap();
    for (CurrencyBalance balance : openingBalances.stream().sorted(BALANCE_ORDER).toList()) {
      BigDecimal signedNet =
          balance.balanceSide() == BalanceSide.DEBIT
              ? balance.netAmount().amount()
              : balance.netAmount().amount().negate();
      runningTotals.put(balance.netAmount().currencyCode(), signedNet);
    }
    return runningTotals;
  }

  private static BalanceSide runningBalanceSide(BigDecimal signedBalance) {
    if (signedBalance.signum() == 0) {
      return BalanceSide.ZERO;
    }
    return signedBalance.signum() > 0 ? BalanceSide.DEBIT : BalanceSide.CREDIT;
  }

  private static LocalDate priorDate(LocalDate date) {
    return date.minusDays(1);
  }

  private static Totals totalsFor(
      Map<CurrencyCode, Totals> totalsByCurrency, CurrencyCode currencyCode) {
    return totalsByCurrency.computeIfAbsent(currencyCode, ignored -> new Totals());
  }

  private static AccountTotals accountTotalsFor(
      Map<AccountCode, AccountTotals> totalsByAccount, DeclaredAccount account) {
    return totalsByAccount.computeIfAbsent(
        account.accountCode(), ignored -> accountTotals(account));
  }

  private static AccountTotals accountTotals(DeclaredAccount account) {
    return new AccountTotals(account);
  }

  private static CurrencyCode reportCurrencyCode(SqliteNativeStatement statement)
      throws SqliteNativeException {
    return new CurrencyCode(
        SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_CURRENCY_CODE));
  }

  private static BigDecimal reportAmount(SqliteNativeStatement statement)
      throws SqliteNativeException {
    return new BigDecimal(
        SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_AMOUNT));
  }

  private static <K, V> Map<K, V> insertionOrderedMap() {
    return new LinkedHashMap<>();
  }

  private static <E> Set<E> insertionOrderedSet() {
    return new LinkedHashSet<>();
  }

  private record LedgerMovement(CurrencyCode currencyCode, BigDecimal debit, BigDecimal credit) {
    private LedgerMovement {
      Objects.requireNonNull(currencyCode, "currencyCode");
      Objects.requireNonNull(debit, "debit");
      Objects.requireNonNull(credit, "credit");
    }
  }

  /** Exact per-account currency totals accumulated while building report rows. */
  private static final class AccountTotals {
    private final DeclaredAccount account;
    private final Map<CurrencyCode, Totals> totalsByCurrency = insertionOrderedMap();

    private AccountTotals(DeclaredAccount account) {
      this.account = Objects.requireNonNull(account, "account");
    }

    private void add(
        CurrencyCode currencyCode, JournalLine.EntrySide entrySide, BigDecimal amount) {
      Totals totals = totalsFor(totalsByCurrency, currencyCode);
      totals.add(entrySide, amount);
    }

    private List<TrialBalanceRow> trialBalanceRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(CurrencyCode::value)))
          .map(
              entry ->
                  new TrialBalanceRow(
                      account,
                      SqliteBalanceMath.currencyBalance(
                          entry.getKey(),
                          entry.getValue().debit,
                          entry.getValue().credit,
                          account.normalBalance())))
          .toList();
    }

    private List<PeriodAccountActivityRow> periodActivityRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(CurrencyCode::value)))
          .map(
              entry ->
                  new PeriodAccountActivityRow(
                      account,
                      SqliteBalanceMath.currencyBalance(
                          entry.getKey(),
                          entry.getValue().debit,
                          entry.getValue().credit,
                          account.normalBalance())))
          .toList();
    }
  }

  /** Running debit and credit totals for one account/currency bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;

    private void add(JournalLine.EntrySide entrySide, BigDecimal amount) {
      if (entrySide == JournalLine.EntrySide.DEBIT) {
        debit = debit.add(amount);
      } else {
        credit = credit.add(amount);
      }
    }
  }
}
