package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Optional;

/** Unified read-only seam over an already-open book boundary; lifecycle stays with the owner. */
public interface BookReadSession {
  /** Inspects the selected book file without mutating it. */
  BookInspection inspectBook();

  /** Reports whether the selected book already carries the explicit initialization marker. */
  boolean isInitialized();

  /** Returns one paginated slice of the declared account registry for one initialized book. */
  AccountPage listAccounts(ListAccountsQuery query);

  /** Looks up one declared account in one initialized book. */
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Looks up one committed posting fact by durable posting identity in one initialized book. */
  Optional<CommittedPosting> findPosting(PostingId postingId);

  /** Returns one filtered page of postings in a stable order from one initialized book. */
  PostingPage listPostings(ListPostingsQuery query);

  /** Computes grouped per-currency balances for one declared account in one initialized book. */
  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query);

  /** Computes one canonical trial-balance report. */
  TrialBalanceReport trialBalance(TrialBalanceQuery query);

  /** Computes one canonical account-ledger report for one declared account. */
  AccountLedgerReport accountLedger(AccountLedgerQuery query, RegisteredAccount account);

  /** Computes one canonical bounded period summary report. */
  PeriodSummaryReport periodSummary(PeriodSummaryQuery query);
}
