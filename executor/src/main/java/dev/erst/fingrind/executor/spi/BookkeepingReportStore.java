package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.util.List;
import java.util.Optional;

/** Computes read-model and reporting views from the selected book. */
public interface BookkeepingReportStore {
  /** Computes grouped per-currency balances for one declared account in one initialized book. */
  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query);

  /** Aggregates exact debit and credit totals by account and currency for one read-time window. */
  List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage);

  /** Computes one canonical trial-balance report. */
  TrialBalanceView trialBalance(TrialBalanceCriteria query);

  /** Computes one canonical account-ledger report for one declared account. */
  AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account);

  /** Computes one canonical bounded period summary report. */
  PeriodSummaryView periodSummary(PeriodSummaryCriteria query);
}
