package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.util.Objects;

/** Facade over focused report readers for trial balance, account ledger, and period summary. */
final class SqliteReportReader {
  private final SqliteTrialBalanceReader trialBalanceReader;
  private final SqliteAccountLedgerReader accountLedgerReader;
  private final SqlitePeriodSummaryReader periodSummaryReader;

  SqliteReportReader(
      SqlitePostingReader postingReader, SqlitePostingBalanceReader postingBalanceReader) {
    Objects.requireNonNull(postingReader, "postingReader");
    Objects.requireNonNull(postingBalanceReader);
    this.trialBalanceReader = new SqliteTrialBalanceReader();
    this.accountLedgerReader = new SqliteAccountLedgerReader(postingReader, postingBalanceReader);
    this.periodSummaryReader = new SqlitePeriodSummaryReader();
  }

  TrialBalanceView trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceCriteria query) {
    return trialBalanceReader.trialBalance(activeDatabase, query);
  }

  AccountLedgerView accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerCriteria query, RegisteredAccount account) {
    return accountLedgerReader.accountLedger(activeDatabase, query, account);
  }

  PeriodSummaryView periodSummary(
      SqliteNativeDatabase activeDatabase, PeriodSummaryCriteria query) {
    return periodSummaryReader.periodSummary(activeDatabase, query);
  }
}
