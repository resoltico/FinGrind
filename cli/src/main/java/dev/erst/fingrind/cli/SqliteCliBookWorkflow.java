package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CommitEntryResult;
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
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.time.Clock;
import java.util.Objects;

/** SQLite-backed CLI workflow that opens one book session per command. */
final class SqliteCliBookWorkflow implements CliBookWorkflow {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliBookWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public OpenBookResult openBook(BookAccess bookAccess) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE,
            CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)) {
      return new BookAdministrationService(bookSession.administrationSession(), clock).openBook();
    }
  }

  @Override
  public RekeyBookResult rekeyBook(
      BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
    try (SqlitePostingFactStore bookSession =
            openBookSession(
                bookAccess,
                SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
                CliBookPassphraseResolver.PromptStyle.SINGLE);
        var replacementPassphrase =
            passphraseResolver.resolve(
                bookAccess.bookFilePath(),
                replacementPassphraseSource,
                CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)) {
      return bookSession.rekeyBook(replacementPassphrase);
    }
  }

  @Override
  public DeclareAccountResult declareAccount(BookAccess bookAccess, DeclareAccountCommand command) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookAdministrationService(bookSession.administrationSession(), clock)
          .declareAccount(command);
    }
  }

  @Override
  public BookInspection inspectBook(BookAccess bookAccess) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).inspectBook();
    }
  }

  @Override
  public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).listAccounts(query);
    }
  }

  @Override
  public GetPostingResult getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).getPosting(postingId);
    }
  }

  @Override
  public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).listPostings(query);
    }
  }

  @Override
  public AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).accountBalance(query);
    }
  }

  @Override
  public TrialBalanceResult trialBalance(BookAccess bookAccess, TrialBalanceQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).trialBalance(query);
    }
  }

  @Override
  public AccountLedgerResult accountLedger(BookAccess bookAccess, AccountLedgerQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).accountLedger(query);
    }
  }

  @Override
  public PeriodSummaryResult periodSummary(BookAccess bookAccess, PeriodSummaryQuery query) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new BookReadService(bookSession.readSession()).periodSummary(query);
    }
  }

  @Override
  public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean initializesBook = plan.beginsWithOpenBook();
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.PLAN_EXECUTION,
            initializesBook
                ? CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET
                : CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return new LedgerPlanService(bookSession, new UuidV7PostingIdGenerator(), clock)
          .execute(plan);
    }
  }

  @Override
  public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_ONLY,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return postingApplicationService(bookSession.postingSession(), clock).preflight(command);
    }
  }

  @Override
  public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
    try (SqlitePostingFactStore bookSession =
        openBookSession(
            bookAccess,
            SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING,
            CliBookPassphraseResolver.PromptStyle.SINGLE)) {
      return postingApplicationService(bookSession.postingSession(), clock).commit(command);
    }
  }

  private SqlitePostingFactStore openBookSession(
      BookAccess bookAccess,
      SqlitePostingFactStore.AccessMode accessMode,
      CliBookPassphraseResolver.PromptStyle promptStyle) {
    return new SqlitePostingFactStore(
        bookAccess.bookFilePath(), passphraseResolver.resolve(bookAccess, promptStyle), accessMode);
  }

  private static PostingApplicationService postingApplicationService(
      PostingBookSession bookSession, Clock clock) {
    return new PostingApplicationService(bookSession, new UuidV7PostingIdGenerator(), clock);
  }
}
