package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite read helpers for account balances and account totals. */
final class SqlitePostingBalanceReader {
  Optional<AccountBalanceView> accountBalance(
      SqliteNativeDatabase activeDatabase, AccountBalanceCriteria query) {
    Optional<RegisteredAccount> account =
        SqliteStatementQueries.findOneAccount(activeDatabase, query.accountCode());
    if (account.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(accountBalance(activeDatabase, query, account.orElseThrow()));
  }

  AccountBalanceView accountBalance(
      SqliteNativeDatabase activeDatabase,
      AccountBalanceCriteria query,
      RegisteredAccount account) {
    return new AccountBalanceView(
        account,
        query.effectiveDateRange(),
        query.postingCoverage(),
        loadCurrencyBalances(activeDatabase, query));
  }

  List<AccountCurrencyTotals> loadAccountTotals(
      SqliteNativeDatabase activeDatabase,
      EffectiveDateRange effectiveDateRange,
      PostingCoverage postingCoverage) {
    String sql = SqlitePostingSql.loadAccountTotals(effectiveDateRange, postingCoverage);
    List<AccountCurrencyTotals> totals = new ArrayList<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      int bindIndex = 1;
      if (effectiveDateRange.effectiveDateFrom().isPresent()) {
        statement.bindText(
            bindIndex,
            CanonicalTemporalText.formatLocalDate(
                effectiveDateRange.effectiveDateFrom().orElseThrow()));
        bindIndex++;
      }
      if (effectiveDateRange.effectiveDateTo().isPresent()) {
        statement.bindText(
            bindIndex,
            CanonicalTemporalText.formatLocalDate(
                effectiveDateRange.effectiveDateTo().orElseThrow()));
      }
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        totals.add(
            new AccountCurrencyTotals(
                SqlitePostingMapper.registeredAccount(statement),
                SqlitePersistedMoneyCodec.readCurrencyUnit(
                    statement, SqlitePostingSql.COL_TOTAL_CURRENCY_CODE),
                statement.columnLong(SqlitePostingSql.COL_TOTAL_DEBIT_MINOR),
                statement.columnLong(SqlitePostingSql.COL_TOTAL_CREDIT_MINOR)));
      }
    }
    return List.copyOf(totals);
  }

  static List<CurrencyUnit> orderedCurrencyCodes(Iterable<CurrencyUnit> currencyCodes) {
    List<CurrencyUnit> ordered = new ArrayList<>();
    currencyCodes.forEach(ordered::add);
    ordered.sort(Comparator.comparing(CurrencyUnit::code));
    return List.copyOf(ordered);
  }

  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceCriteria query) {
    String sql = SqlitePostingSql.loadAccountLinesForBalance(query);
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableTotalsByCurrency();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindAccountBalanceQuery(statement, query);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        JournalLine.EntrySide side = readEntrySide(statement);
        CurrencyUnit currencyCode = readCurrencyCode(statement);
        long amountMinor = readAmountMinor(statement);
        Totals totals = totalsFor(totalsByCurrency, currencyCode);
        if (side == JournalLine.EntrySide.DEBIT) {
          totals.debit = Math.addExact(totals.debit, amountMinor);
        } else {
          totals.credit = Math.addExact(totals.credit, amountMinor);
        }
      }
    }
    List<CurrencyBalance> balances = new ArrayList<>();
    for (CurrencyUnit currencyCode : orderedCurrencyCodes(totalsByCurrency.keySet())) {
      Totals totals = Objects.requireNonNull(totalsByCurrency.get(currencyCode));
      balances.add(BalanceMath.currencyBalance(currencyCode, totals.debit, totals.credit));
    }
    return List.copyOf(balances);
  }

  private static Map<CurrencyUnit, Totals> mutableTotalsByCurrency() {
    return SqliteReportRowValues.insertionOrderedMap();
  }

  private static Totals totalsFor(
      Map<CurrencyUnit, Totals> totalsByCurrency, CurrencyUnit currencyCode) {
    Totals totals = totalsByCurrency.get(currencyCode);
    if (totals != null) {
      return totals;
    }
    Totals createdTotals = new Totals();
    totalsByCurrency.put(currencyCode, createdTotals);
    return createdTotals;
  }

  private static void bindAccountBalanceQuery(
      SqliteNativeStatement statement, AccountBalanceCriteria query) {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (query.postingCoverage().isNonClosingOnly()) {
      statement.bindText(
          bindIndex, dev.erst.fingrind.core.PostingKind.PERIOD_RESULT_TRANSFER.wireValue());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateFrom().orElseThrow()));
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateTo().orElseThrow()));
    }
  }

  private static JournalLine.EntrySide readEntrySide(SqliteNativeStatement statement) {
    return JournalLine.EntrySide.fromWireValue(SqlitePostingMapper.requiredText(statement, 0));
  }

  private static CurrencyUnit readCurrencyCode(SqliteNativeStatement statement) {
    return SqlitePersistedMoneyCodec.readCurrencyUnit(statement, 1);
  }

  private static long readAmountMinor(SqliteNativeStatement statement) {
    return statement.columnLong(2);
  }

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private long debit;
    private long credit;
  }
}
