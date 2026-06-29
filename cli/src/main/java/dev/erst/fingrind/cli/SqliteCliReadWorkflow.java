package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
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
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.TaxReadService;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteReadSessions;
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
    return withBookRead(bookAccess, BookReadService::inspectBook);
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withBookRead(bookAccess, service -> service.listAccounts(query));
  }

  @Override
  public ContractDecision<ListTaxRegistrationsResult> listTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query) {
    return withTaxRead(bookAccess, service -> service.listTaxRegistrations(query));
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(BookAccess bookAccess, PostingId postingId) {
    return withBookRead(bookAccess, service -> service.getPosting(postingId));
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withBookRead(bookAccess, service -> service.listPostings(query));
  }

  @Override
  public ContractDecision<TaxObligationResult> taxObligation(
      BookAccess bookAccess, TaxObligationQuery query) {
    return withTaxRead(bookAccess, service -> service.taxObligation(query));
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withBookRead(bookAccess, service -> service.accountBalance(query));
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withBookRead(bookAccess, service -> service.trialBalance(query));
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withBookRead(bookAccess, service -> service.accountLedger(query));
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withBookRead(bookAccess, service -> service.periodSummary(query));
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return withBookRead(bookAccess, service -> service.financialPosition(query));
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return withBookRead(bookAccess, service -> service.incomeStatement(query));
  }

  @Override
  public ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query) {
    return withBookRead(bookAccess, service -> service.cashFlowStatement(query));
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return withBookRead(bookAccess, service -> service.changesInEquity(query));
  }

  private <T> ContractDecision<T> withBookRead(
      BookAccess bookAccess, Function<BookReadService, T> work) {
    return withRead(bookAccess, bookSession -> work.apply(new BookReadService(bookSession)));
  }

  private <T> ContractDecision<T> withTaxRead(
      BookAccess bookAccess, Function<TaxReadService, T> work) {
    return withRead(bookAccess, bookSession -> work.apply(new TaxReadService(bookSession)));
  }

  private <T> ContractDecision<T> withRead(
      BookAccess bookAccess, Function<SqliteReadSession, T> work) {
    return SqliteCliWorkflowSessions.withReadSession(
        SqliteReadSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        work);
  }
}
