package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteReadSessions;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import java.util.Objects;
import java.util.function.Function;

/** SQLite-backed read workflow for inspection, listings, and statement queries. */
final class SqliteCliReadWorkflow implements CliBookReadWorkflow {
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliReadWorkflow(CliBookPassphraseResolver passphraseResolver) {
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    return withRead(bookAccess, bookSession -> new BookReadService(bookSession).inspectBook());
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).listAccounts(query));
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(BookAccess bookAccess, PostingId postingId) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).getPosting(postingId));
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).listPostings(query));
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).accountBalance(query));
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).trialBalance(query));
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).accountLedger(query));
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).periodSummary(query));
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).financialPosition(query));
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).incomeStatement(query));
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return withRead(
        bookAccess, bookSession -> new BookReadService(bookSession).changesInEquity(query));
  }

  private <T> ContractDecision<T> withRead(
      BookAccess bookAccess, Function<SqliteReadSession, T> work) {
    return SqliteCliWorkflowSessions.withReadSession(
        SqliteReadSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        work);
  }
}
