package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import java.math.BigDecimal;
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
      Comparator.comparing(balance -> balance.netAmount().currencyCode().value());
  static final Comparator<AccountCode> ACCOUNT_CODE_ORDER =
      Comparator.comparing(AccountCode::value);
  static final Comparator<CurrencyCode> CURRENCY_CODE_ORDER =
      Comparator.comparing(CurrencyCode::value);

  private SqliteReportRowValues() {}

  static CurrencyCode reportCurrencyCode(SqliteNativeStatement statement) {
    return new CurrencyCode(
        SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_CURRENCY_CODE));
  }

  static BigDecimal reportAmount(SqliteNativeStatement statement) {
    return new BigDecimal(
        SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_AMOUNT));
  }

  static <K, V> Map<K, V> insertionOrderedMap() {
    return new LinkedHashMap<>();
  }

  static <E> Set<E> insertionOrderedSet() {
    return new LinkedHashSet<>();
  }

  static Totals totalsFor(Map<CurrencyCode, Totals> totalsByCurrency, CurrencyCode currencyCode) {
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
    private final Map<CurrencyCode, Totals> totalsByCurrency = insertionOrderedMap();

    private AccountTotals(RegisteredAccount account) {
      this.account = Objects.requireNonNull(account, "account");
    }

    void add(CurrencyCode currencyCode, JournalLine.EntrySide entrySide, BigDecimal amount) {
      totalsFor(totalsByCurrency, currencyCode).add(entrySide, amount);
    }

    List<TrialBalanceRowView> trialBalanceRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(CURRENCY_CODE_ORDER))
          .map(
              entry ->
                  new TrialBalanceRowView(
                      account,
                      SqliteBalanceMath.currencyBalance(
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
                      SqliteBalanceMath.currencyBalance(
                          entry.getKey(), entry.getValue().debit, entry.getValue().credit)))
          .toList();
    }
  }

  /** Running debit and credit totals for one account/currency bucket. */
  static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;

    void add(JournalLine.EntrySide entrySide, BigDecimal amount) {
      if (entrySide == JournalLine.EntrySide.DEBIT) {
        debit = debit.add(amount);
      } else {
        credit = credit.add(amount);
      }
    }

    BigDecimal debit() {
      return debit;
    }

    BigDecimal credit() {
      return credit;
    }
  }
}
