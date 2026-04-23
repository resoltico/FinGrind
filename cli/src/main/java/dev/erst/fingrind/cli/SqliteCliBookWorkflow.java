package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqliteBookSessions;
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
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_CREATE,
        CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET,
        bookSession ->
            new BookAdministrationService(bookSession.administrationSession(), clock).openBook());
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
    return withBookSessionDecision(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession ->
            passphraseResolver
                .resolve(
                    bookAccess.bookFilePath(),
                    replacementPassphraseSource,
                    CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                .fold(
                    replacementPassphrase -> {
                      try (SqliteBookPassphrase ignored = replacementPassphrase) {
                        return ContractDecision.accepted(
                            bookSession.rekeyBook(replacementPassphrase));
                      }
                    },
                    ContractDecision::rejected));
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession ->
            new BookAdministrationService(bookSession.administrationSession(), clock)
                .declareAccount(command));
  }

  @Override
  public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).inspectBook());
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).listAccounts(query));
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).getPosting(postingId));
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).listPostings(query));
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).accountBalance(query));
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).trialBalance(query));
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).accountLedger(query));
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession -> new BookReadService(bookSession.readSession()).periodSummary(query));
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean initializesBook = plan.beginsWithOpenBook();
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.PLAN_EXECUTION,
        initializesBook
            ? CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET
            : CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession ->
            new LedgerPlanService(bookSession, new UuidV7PostingIdGenerator(), clock)
                .execute(plan));
  }

  @Override
  public ContractDecision<PreflightEntryResult> preflight(
      BookAccess bookAccess, PostEntryCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_ONLY,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession ->
            postingApplicationService(bookSession.postingSession(), clock).preflight(command));
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    return withBookSession(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        CliBookPassphraseResolver.PromptStyle.SINGLE,
        bookSession ->
            postingApplicationService(bookSession.postingSession(), clock).commit(command));
  }

  private ContractDecision<SqliteBookSession> openBookSession(
      BookAccess bookAccess,
      SqliteBookSessionMode accessMode,
      CliBookPassphraseResolver.PromptStyle promptStyle) {
    return passphraseResolver
        .resolve(bookAccess, promptStyle)
        .fold(
            bookPassphrase ->
                SqliteBookSessions.openResolved(
                    bookAccess.bookFilePath(), bookPassphrase, accessMode),
            ContractDecision::rejected);
  }

  private <T> ContractDecision<T> withBookSession(
      BookAccess bookAccess,
      SqliteBookSessionMode accessMode,
      CliBookPassphraseResolver.PromptStyle promptStyle,
      Function<SqliteBookSession, T> work) {
    return openBookSession(bookAccess, accessMode, promptStyle)
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
      CliBookPassphraseResolver.PromptStyle promptStyle,
      Function<SqliteBookSession, ContractDecision<T>> work) {
    return openBookSession(bookAccess, accessMode, promptStyle)
        .fold(
            bookSession -> {
              try (SqliteBookSession ignored = bookSession) {
                return work.apply(bookSession);
              }
            },
            ContractDecision::rejected);
  }

  private static PostingApplicationService postingApplicationService(
      PostingBookSession bookSession, Clock clock) {
    return new PostingApplicationService(bookSession, new UuidV7PostingIdGenerator(), clock);
  }
}
