package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PeriodCloseService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqliteBookSessions;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

/** SQLite-backed CLI workflow that opens one book session per command. */
final class SqliteCliBookWorkflow implements CliBookWorkflow {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliBookWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_CREATE,
        SqlitePassphraseIntent.NEW_SECRET,
        bookSession ->
            dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator
                .toPublished(
                    new BookAdministrationService(bookSession, clock)
                        .openBook(
                            dev.erst.fingrind.executor.bookkeeping
                                .BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
    return withBookSessionDecision(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            bookSession.rekeyBook(
                replacementPassphraseSource, passphraseResolver, clock.instant()));
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new BookAdministrationService(bookSession, clock)
                    .declareAccount(
                        BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<ClosePeriodResult> closePeriod(
      BookAccess bookAccess, ClosePeriodCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new PeriodCloseService(
                        bookSession,
                        bookSession,
                        bookSession,
                        bookSession,
                        new UuidV7PostingIdGenerator(),
                        clock)
                    .closePeriod(
                        BookkeepingPublishedLanguageTranslator.fromPublished(command),
                        BookkeepingPublishedLanguageTranslator.closingEquityAccountCode(command))));
  }

  @Override
  public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).inspectBook());
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).listAccounts(query));
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).getPosting(postingId));
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).listPostings(query));
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).accountBalance(query));
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).trialBalance(query));
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).accountLedger(query));
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).periodSummary(query));
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).financialPosition(query));
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).incomeStatement(query));
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession -> new BookReadService(bookSession).changesInEquity(query));
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean initializesBook = plan.beginsWithOpenBook();
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.PLAN_EXECUTION,
        initializesBook
            ? SqlitePassphraseIntent.NEW_SECRET
            : SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            new LedgerPlanService(
                    bookSession,
                    bookSession,
                    bookSession,
                    bookSession,
                    bookSession,
                    new UuidV7PostingIdGenerator(),
                    clock)
                .execute(plan));
  }

  @Override
  public ContractDecision<PreflightEntryResult> preflight(
      BookAccess bookAccess, PostEntryCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            postingApplicationService(bookSession, clock)
                .preflight(BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        SqlitePassphraseIntent.EXISTING_SECRET,
        bookSession ->
            postingApplicationService(bookSession, clock)
                .commit(BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  private ContractDecision<SqliteBookSession> openBookSession(
      BookAccess bookAccess,
      SqliteBookSessionMode accessMode,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.openResolved(
        bookAccess, accessMode, passphraseResolver, passphraseIntent);
  }

  private <T> ContractDecision<T> withBookSession(
      BookAccess bookAccess,
      SqliteBookSessionMode accessMode,
      SqlitePassphraseIntent passphraseIntent,
      Function<SqliteBookSession, T> work) {
    return openBookSession(bookAccess, accessMode, passphraseIntent)
        .fold(
            bookSession -> {
              try (SqliteBookSession ignored = bookSession) {
                return ContractDecision.accepted(work.apply(bookSession));
              }
            },
            ContractDecision::rejected);
  }

  private <T> ContractDecision<T> withBookSessionDecision(
      BookAccess bookAccess,
      SqliteBookSessionMode accessMode,
      SqlitePassphraseIntent passphraseIntent,
      Function<SqliteBookSession, ContractDecision<T>> work) {
    return openBookSession(bookAccess, accessMode, passphraseIntent)
        .fold(
            bookSession -> {
              try (SqliteBookSession ignored = bookSession) {
                return work.apply(bookSession);
              }
            },
            ContractDecision::rejected);
  }

  private static PostingApplicationService postingApplicationService(
      SqliteBookSession bookSession, Clock clock) {
    return new PostingApplicationService(
        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock);
  }
}
