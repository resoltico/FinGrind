package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
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
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PeriodCloseService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.ProtectedBookMaintenanceService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqliteBookSessions;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePeriodCloseSession;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSession;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteRekeySession;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** SQLite-backed CLI workflow that opens one book session per command. */
final class SqliteCliBookWorkflow implements CliBookWorkflow {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;
  private final ProtectedBookMaintenanceService maintenanceService;

  SqliteCliBookWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.maintenanceService =
        new ProtectedBookMaintenanceService(
            this.clock, new SqliteProtectedBookMaintenanceStore(this.passphraseResolver));
  }

  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    return withAdministrationSession(
        SqliteBookSessions.openResolvedAdministration(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_CREATE,
            passphraseResolver,
            SqlitePassphraseIntent.NEW_SECRET),
        bookSession ->
            dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator
                .toPublished(
                    new BookAdministrationService(bookSession, bookSession, bookSession, clock)
                        .openBook(
                            dev.erst.fingrind.executor.bookkeeping
                                .BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
    return withRekeySession(
        SqliteBookSessions.openResolvedRekey(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            bookSession.rekeyBook(
                replacementPassphraseSource, passphraseResolver, clock.instant()));
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return maintenanceService.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath);
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    return maintenanceService.restoreBook(bookFilePath, backupFilePath, backupBookKeyFilePath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
    return maintenanceService.inspectRekeyRollback(bookFilePath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      Path bookFilePath, @Nullable Path rollbackArtifactPath) {
    return maintenanceService.deleteRekeyRollback(bookFilePath, rollbackArtifactPath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource) {
    return maintenanceService.restoreRekeyRollback(
        bookFilePath, rollbackArtifactPath, expectedPassphraseSource);
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return withAdministrationSession(
        SqliteBookSessions.openResolvedAdministration(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new BookAdministrationService(bookSession, bookSession, bookSession, clock)
                    .declareAccount(
                        BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<ClosePeriodResult> closePeriod(
      BookAccess bookAccess, ClosePeriodCommand command) {
    return withPeriodCloseSession(
        SqliteBookSessions.openResolvedPeriodClose(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new PeriodCloseService(
                        bookSession,
                        bookSession,
                        bookSession,
                        bookSession,
                        new UuidV7PostingIdGenerator(),
                        clock)
                    .closePeriod(BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).inspectBook());
  }

  @Override
  public ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).listAccounts(query));
  }

  @Override
  public ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, dev.erst.fingrind.core.PostingId postingId) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).getPosting(postingId));
  }

  @Override
  public ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).listPostings(query));
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).accountBalance(query));
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).trialBalance(query));
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).accountLedger(query));
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).periodSummary(query));
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).financialPosition(query));
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).incomeStatement(query));
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return withReadSession(
        SqliteBookSessions.openResolvedRead(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession -> new BookReadService(bookSession).changesInEquity(query));
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean initializesBook = plan.beginsWithOpenBook();
    return withPlanExecutionSession(
        SqliteBookSessions.openResolvedPlanExecution(
            bookAccess,
            passphraseResolver,
            initializesBook
                ? SqlitePassphraseIntent.NEW_SECRET
                : SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            new LedgerPlanService(
                    bookSession,
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
    return withPostingSession(
        SqliteBookSessions.openResolvedPosting(
            bookAccess,
            SqliteBookSessionMode.READ_ONLY,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            postingApplicationService(bookSession, clock)
                .preflight(BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    return withPostingSession(
        SqliteBookSessions.openResolvedPosting(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            postingApplicationService(bookSession, clock)
                .commit(BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  private static <T> ContractDecision<T> withAdministrationSession(
      ContractDecision<SqliteAdministrationSession> decision,
      Function<SqliteAdministrationSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteAdministrationSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> withReadSession(
      ContractDecision<SqliteReadSession> decision, Function<SqliteReadSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteReadSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> withPostingSession(
      ContractDecision<SqlitePostingSession> decision, Function<SqlitePostingSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePostingSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> withPeriodCloseSession(
      ContractDecision<SqlitePeriodCloseSession> decision,
      Function<SqlitePeriodCloseSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePeriodCloseSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> withPlanExecutionSession(
      ContractDecision<SqlitePlanExecutionSession> decision,
      Function<SqlitePlanExecutionSession, T> work) {
    return decision.fold(
        bookSession -> {
          try (SqlitePlanExecutionSession ignored = bookSession) {
            return ContractDecision.accepted(work.apply(bookSession));
          }
        },
        ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> withRekeySession(
      ContractDecision<SqliteRekeySession> decision,
      Function<SqliteRekeySession, ContractDecision<T>> work) {
    return decision.fold(
        bookSession -> {
          try (SqliteRekeySession ignored = bookSession) {
            return work.apply(bookSession);
          }
        },
        ContractDecision::rejected);
  }

  private static PostingApplicationService postingApplicationService(
      SqlitePostingSession bookSession, Clock clock) {
    return new PostingApplicationService(
        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock);
  }
}
