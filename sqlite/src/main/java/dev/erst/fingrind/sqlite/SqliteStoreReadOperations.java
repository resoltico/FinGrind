package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Facade that routes SQLite reads to narrower query and reporting helpers. */
final class SqliteStoreReadOperations {
  private final SqliteStoreQueryOperations queryOperations;
  private final SqliteStoreReportOperations reportOperations;

  SqliteStoreReadOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(lifecycle, "lifecycle");
    this.queryOperations = new SqliteStoreQueryOperations(context, lifecycle);
    this.reportOperations = new SqliteStoreReportOperations(context, lifecycle);
  }

  BookInspection inspectBook() {
    return queryOperations.inspectBook();
  }

  boolean isInitialized() {
    return queryOperations.isInitialized();
  }

  Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return queryOperations.findAccount(accountCode);
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return queryOperations.findAccounts(accountCodes);
  }

  AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return queryOperations.listAccounts(query);
  }

  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return queryOperations.findExistingPosting(idempotencyKey);
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    return queryOperations.findPosting(postingId);
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return queryOperations.findReversalFor(priorPostingId);
  }

  PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return queryOperations.listPostings(query);
  }

  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    return reportOperations.accountBalance(query);
  }

  TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    return reportOperations.trialBalance(query);
  }

  AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    return reportOperations.accountLedger(query, account);
  }

  PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    return reportOperations.periodSummary(query);
  }
}
