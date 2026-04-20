package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared accumulation and row-mapping helpers for SQLite-backed report readers. */
final class SqliteReportDataSupport {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyCode().value());
  static final Comparator<AccountCode> ACCOUNT_CODE_ORDER =
      Comparator.comparing(AccountCode::value);
  static final Comparator<CurrencyCode> CURRENCY_CODE_ORDER =
      Comparator.comparing(CurrencyCode::value);

  private SqliteReportDataSupport() {}

  static CurrencyCode reportCurrencyCode(SqliteNativeStatement statement)
      throws SqliteNativeException {
    return new CurrencyCode(
        SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_CURRENCY_CODE));
  }

  static BigDecimal reportAmount(SqliteNativeStatement statement) throws SqliteNativeException {
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
      Map<AccountCode, AccountTotals> totalsByAccount, DeclaredAccount account) {
    return totalsByAccount.computeIfAbsent(
        account.accountCode(), ignored -> new AccountTotals(account));
  }

  /** Exact per-account currency totals accumulated while building report rows. */
  static final class AccountTotals {
    private final DeclaredAccount account;
    private final Map<CurrencyCode, Totals> totalsByCurrency = insertionOrderedMap();

    private AccountTotals(DeclaredAccount account) {
      this.account = Objects.requireNonNull(account, "account");
    }

    void add(CurrencyCode currencyCode, JournalLine.EntrySide entrySide, BigDecimal amount) {
      totalsFor(totalsByCurrency, currencyCode).add(entrySide, amount);
    }

    List<TrialBalanceRow> trialBalanceRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(CURRENCY_CODE_ORDER))
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

    List<PeriodAccountActivityRow> periodActivityRows() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(CURRENCY_CODE_ORDER))
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
