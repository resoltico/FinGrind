package dev.erst.fingrind.sqlite;

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
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
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

  AccountPage listAccounts(ListAccountsQuery query) {
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

  PostingPage listPostings(ListPostingsQuery query) {
    return queryOperations.listPostings(query);
  }

  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return reportOperations.accountBalance(query);
  }

  TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return reportOperations.trialBalance(query);
  }

  AccountLedgerReport accountLedger(AccountLedgerQuery query, RegisteredAccount account) {
    return reportOperations.accountLedger(query, account);
  }

  PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return reportOperations.periodSummary(query);
  }
}
