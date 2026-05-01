package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadSession;
import java.util.Objects;
import java.util.Optional;

/** Narrow unified read-session view over one SQLite-backed store. */
final class SqliteBookReadSessionView implements BookReadSession {
  private final SqliteStoreReadOperations readOperations;

  SqliteBookReadSessionView(SqliteStoreReadOperations readOperations) {
    this.readOperations = Objects.requireNonNull(readOperations, "readOperations");
  }

  @Override
  public BookInspection inspectBook() {
    return readOperations.inspectBook();
  }

  @Override
  public boolean isInitialized() {
    return readOperations.isInitialized();
  }

  @Override
  public AccountPage listAccounts(ListAccountsQuery query) {
    return readOperations.listAccounts(query);
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return readOperations.findAccount(accountCode);
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return readOperations.findPosting(postingId);
  }

  @Override
  public PostingPage listPostings(ListPostingsQuery query) {
    return readOperations.listPostings(query);
  }

  @Override
  public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return readOperations.accountBalance(query);
  }

  @Override
  public TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return readOperations.trialBalance(query);
  }

  @Override
  public AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    return readOperations.accountLedger(query, account);
  }

  @Override
  public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return readOperations.periodSummary(query);
  }
}
