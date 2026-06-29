package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.FiscalYearCloseService;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.TaxAdministrationService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSessions;
import dev.erst.fingrind.sqlite.SqlitePostingSessions;
import dev.erst.fingrind.sqlite.SqliteReportingPeriodCloseSessions;
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
                        BookkeepingRequestPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<DeclareTaxRegistrationResult> declareTaxRegistration(
      BookAccess bookAccess, DeclareTaxRegistrationCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSession(
        SqliteAdministrationSessions.openResolved(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            new TaxAdministrationService(bookSession, bookSession, bookSession, clock)
                .declareTaxRegistration(command));
  }

  @Override
  public ContractDecision<InterimResultSweepResult> interimResultSweep(
      BookAccess bookAccess, InterimResultSweepCommand command) {
    return SqliteCliWorkflowSessions.withReportingPeriodCloseSession(
        SqliteReportingPeriodCloseSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new InterimResultSweepService(
                        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock)
                    .interimResultSweep(
                        BookkeepingRequestPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<FiscalYearCloseResult> fiscalYearClose(
      BookAccess bookAccess, FiscalYearCloseCommand command) {
    return SqliteCliWorkflowSessions.withReportingPeriodCloseSession(
        SqliteReportingPeriodCloseSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new FiscalYearCloseService(
                        bookSession, bookSession, new UuidV7PostingIdGenerator(), clock)
                    .fiscalYearClose(
                        BookkeepingRequestPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    boolean ensuresBook = plan.beginsWithEnsureBook();
    return SqliteCliWorkflowSessions.withPlanExecutionSession(
        SqlitePlanExecutionSessions.openResolved(
            bookAccess,
            passphraseResolver,
            ensuresBook
                ? SqlitePassphraseIntent.PLAN_SETUP_SECRET
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
