package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared accumulation and row-mapping helpers for SQLite-backed report readers. */
final class SqliteReportRowValues {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyUnit().code());
  static final Comparator<AccountCode> ACCOUNT_CODE_ORDER =
      Comparator.comparing(AccountCode::value);
  static final Comparator<CurrencyUnit> CURRENCY_CODE_ORDER =
      Comparator.comparing(CurrencyUnit::code);

  private SqliteReportRowValues() {}

  static CurrencyUnit reportCurrencyCode(SqliteNativeStatement statement) {
    return SqlitePersistedMoneyCodec.readCurrencyUnit(
        statement, SqlitePostingColumnIndexes.COL_REPORT_CURRENCY_CODE);
  }

  static long reportAmountMinor(SqliteNativeStatement statement) {
    return statement.columnLong(SqlitePostingColumnIndexes.COL_REPORT_AMOUNT_MINOR);
  }

  static <K, V> Map<K, V> insertionOrderedMap() {
    return new LinkedHashMap<>();
  }

  static <E> Set<E> insertionOrderedSet() {
    return new LinkedHashSet<>();
  }

  static Totals totalsFor(Map<CurrencyUnit, Totals> totalsByCurrency, CurrencyUnit currencyCode) {
    return totalsByCurrency.computeIfAbsent(currencyCode, ignored -> new Totals());
  }

  static AccountTotals accountTotalsFor(
      Map<AccountCode, AccountTotals> totalsByAccount, RegisteredAccount account) {
    return totalsByAccount.computeIfAbsent(
        account.accountCode(), ignored -> new AccountTotals(account));
  }

  /** Exact per-account currency totals accumulated while building report rows. */
  static final class AccountTotals {
    private final RegisteredAccount account;
    private final Map<CurrencyUnit, Totals> totalsByCurrency = insertionOrderedMap();

    private AccountTotals(RegisteredAccount account) {
      this.account = Objects.requireNonNull(account, "account");
    }

    void add(CurrencyUnit currencyCode, JournalLine.EntrySide entrySide, long amountMinor) {
      totalsFor(totalsByCurrency, currencyCode).add(entrySide, amountMinor);
    }

    List<TrialBalanceRowView> trialBalanceRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(CURRENCY_CODE_ORDER))
          .map(
              entry ->
                  new TrialBalanceRowView(
                      account,
                      BalanceMath.currencyBalance(
                          entry.getKey(), entry.getValue().debit, entry.getValue().credit)))
          .toList();
    }

    List<PeriodAccountActivityView> periodActivityRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(CURRENCY_CODE_ORDER))
          .map(
              entry ->
                  new PeriodAccountActivityView(
                      account,
                      BalanceMath.currencyBalance(
                          entry.getKey(), entry.getValue().debit, entry.getValue().credit)))
          .toList();
    }
  }

  /** Running debit and credit totals for one account/currency bucket. */
  static final class Totals {
    private long debit;
    private long credit;

    void add(JournalLine.EntrySide entrySide, long amountMinor) {
      if (entrySide == JournalLine.EntrySide.DEBIT) {
        debit = Math.addExact(debit, amountMinor);
      } else {
        credit = Math.addExact(credit, amountMinor);
      }
    }

    long debit() {
      return debit;
    }

    long credit() {
      return credit;
    }
  }
}
