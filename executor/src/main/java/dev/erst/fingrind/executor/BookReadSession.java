package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.util.Optional;

/** Unified read-only seam over an already-open book boundary; lifecycle stays with the owner. */
public interface BookReadSession {
  /** Inspects the selected book file without mutating it. */
  BookInspection inspectBook();

  /** Reports whether the selected book already carries the explicit initialization marker. */
  boolean isInitialized();

  /** Returns one paginated slice of the declared account registry for one initialized book. */
  AccountRegistryPage listAccounts(AccountRegistryQuery query);

  /** Looks up one declared account in one initialized book. */
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Looks up one committed posting fact by durable posting identity in one initialized book. */
  Optional<CommittedPosting> findPosting(PostingId postingId);

  /** Returns one filtered page of postings in a stable order from one initialized book. */
  PostingHistoryPage listPostings(PostingHistoryQuery query);

  /** Computes grouped per-currency balances for one declared account in one initialized book. */
  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query);

  /** Computes one canonical trial-balance report. */
  TrialBalanceView trialBalance(TrialBalanceCriteria query);

  /** Computes one canonical account-ledger report for one declared account. */
  AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account);

  /** Computes one canonical bounded period summary report. */
  PeriodSummaryView periodSummary(PeriodSummaryCriteria query);
}
