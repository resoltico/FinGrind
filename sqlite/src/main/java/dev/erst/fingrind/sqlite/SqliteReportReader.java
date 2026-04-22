package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import java.util.Objects;

/** Facade over focused report readers for trial balance, account ledger, and period summary. */
final class SqliteReportReader {
  private final SqliteTrialBalanceReader trialBalanceReader;
  private final SqliteAccountLedgerReader accountLedgerReader;
  private final SqlitePeriodSummaryReader periodSummaryReader;

  SqliteReportReader(SqlitePostingReader postingReader) {
    Objects.requireNonNull(postingReader, "postingReader");
    this.trialBalanceReader = new SqliteTrialBalanceReader();
    this.accountLedgerReader = new SqliteAccountLedgerReader(postingReader);
    this.periodSummaryReader = new SqlitePeriodSummaryReader();
  }

  TrialBalanceReport trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceQuery query) {
    return trialBalanceReader.trialBalance(activeDatabase, query);
  }

  AccountLedgerReport accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account) {
    return accountLedgerReader.accountLedger(activeDatabase, query, account);
  }

  PeriodSummaryReport periodSummary(SqliteNativeDatabase activeDatabase, PeriodSummaryQuery query) {
    return periodSummaryReader.periodSummary(activeDatabase, query);
  }
}
