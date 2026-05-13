package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.time.Instant;
import java.util.Optional;

/** Public executor-facing store boundary for one selected book. */
public interface BookStore extends PostingValidationStore {
  /** Explicitly initializes one new book if the selected path is currently empty. */
  BookOpeningOutcome openBook(Instant initializedAt);

  /** Declares or reactivates one account in the selected book. */
  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      Instant declaredAt);

  /** Returns one paginated slice of the declared account registry for one initialized book. */
  AccountRegistryPage listAccounts(AccountRegistryQuery query);

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

  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator);

  /** Attempts one durable close-period commit and returns the administration outcome. */
  PeriodCloseOutcome closePeriod(
      PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator);
}
