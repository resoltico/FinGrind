package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferCommand;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PeriodResultTransferService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePeriodResultTransferSessions;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSessions;
import dev.erst.fingrind.sqlite.SqlitePostingSessions;
import java.time.Clock;
import java.util.Objects;

/** SQLite-backed mutation workflow for administrative accounting and posting flows. */
final class SqliteCliMutationWorkflow implements CliBookMutationWorkflow {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliMutationWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSession(
        SqliteAdministrationSessions.openResolved(
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
  public ContractDecision<PeriodResultTransferResult> transferPeriodResult(
      BookAccess bookAccess, PeriodResultTransferCommand command) {
    return SqliteCliWorkflowSessions.withPeriodResultTransferSession(
        SqlitePeriodResultTransferSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new PeriodResultTransferService(
                        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock)
                    .transferPeriodResult(
                        BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean initializesBook = plan.beginsWithOpenBook();
    return SqliteCliWorkflowSessions.withPlanExecutionSession(
        SqlitePlanExecutionSessions.openResolved(
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
    return SqliteCliWorkflowSessions.withPostingSession(
        SqlitePostingSessions.openResolved(
            bookAccess,
            SqliteBookSessionMode.READ_ONLY,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            SqliteCliWorkflowSessions.postingApplicationService(bookSession, clock)
                .preflight(command));
  }

  @Override
  public ContractDecision<CommitEntryResult> commit(
      BookAccess bookAccess, PostEntryCommand command) {
    return SqliteCliWorkflowSessions.withPostingSession(
        SqlitePostingSessions.openResolved(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            SqliteCliWorkflowSessions.postingApplicationService(bookSession, clock)
                .commit(command));
  }
}
