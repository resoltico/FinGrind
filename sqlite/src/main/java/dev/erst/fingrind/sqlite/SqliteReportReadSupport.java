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
final class SqliteReportReadSupport {
  private final SqliteTrialBalanceReadSupport trialBalanceReader;
  private final SqliteAccountLedgerReadSupport accountLedgerReader;
  private final SqlitePeriodSummaryReadSupport periodSummaryReader;

  SqliteReportReadSupport(SqlitePostingReadSupport postingReadSupport) {
    Objects.requireNonNull(postingReadSupport, "postingReadSupport");
    this.trialBalanceReader = new SqliteTrialBalanceReadSupport();
    this.accountLedgerReader = new SqliteAccountLedgerReadSupport(postingReadSupport);
    this.periodSummaryReader = new SqlitePeriodSummaryReadSupport();
  }

  TrialBalanceReport trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceQuery query)
      throws SqliteNativeException {
    return trialBalanceReader.trialBalance(activeDatabase, query);
  }

  AccountLedgerReport accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    return accountLedgerReader.accountLedger(activeDatabase, query, account);
  }

  PeriodSummaryReport periodSummary(SqliteNativeDatabase activeDatabase, PeriodSummaryQuery query)
      throws SqliteNativeException {
    return periodSummaryReader.periodSummary(activeDatabase, query);
  }
}
