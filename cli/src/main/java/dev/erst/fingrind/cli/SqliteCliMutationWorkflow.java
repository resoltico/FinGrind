package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationRequest;
import dev.erst.fingrind.executor.BookWorkflowExecutionDependencies;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSession;
import dev.erst.fingrind.sqlite.SqlitePlanExecutionSessions;
import dev.erst.fingrind.sqlite.SqlitePostingSessions;
import java.time.Clock;
import java.util.Objects;

/** SQLite-backed mutation workflow that delegates each bounded mutation family to its owner. */
final class SqliteCliMutationWorkflow implements CliBookMutationWorkflow {
  private static final AttestationOperationAuthorizer READ_ONLY_PLAN_AUTHORIZER =
      new ReadOnlyPlanAuthorizer();

  /** Fails closed if a plan classified as read-only ever attempts a protected-book mutation. */
  static final class ReadOnlyPlanAuthorizer implements AttestationOperationAuthorizer {
    @Override
    public AttestationEvidence authorize(AttestationOperationRequest request) {
      throw new IllegalStateException(
          "A credential-free ledger plan must not authorize a protected-book mutation.");
    }
  }

  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;
  private final SqliteCliAdministrationMutations administrationMutations;
  private final SqliteCliReportingPeriodCloseMutations reportingPeriodCloseMutations;

  SqliteCliMutationWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    administrationMutations = new SqliteCliAdministrationMutations(clock, passphraseResolver);
    reportingPeriodCloseMutations =
        new SqliteCliReportingPeriodCloseMutations(clock, passphraseResolver);
  }

  @Override
  public ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return administrationMutations.declareAccount(bookAccess, command);
  }

  @Override
  public ContractDecision<AmendAccountResult> amendAccount(
      BookAccess bookAccess, AmendAccountCommand command) {
    return administrationMutations.amendAccount(bookAccess, command);
  }

  @Override
  public ContractDecision<RetireAccountResult> retireAccount(
      BookAccess bookAccess, RetireAccountCommand command) {
    return administrationMutations.retireAccount(bookAccess, command);
  }

  @Override
  public ContractDecision<DeclareTaxRegistrationResult> declareTaxRegistration(
      BookAccess bookAccess, DeclareTaxRegistrationCommand command) {
    return administrationMutations.declareTaxRegistration(bookAccess, command);
  }

  @Override
  public ContractDecision<InterimResultSweepResult> interimResultSweep(
      BookAccess bookAccess, InterimResultSweepCommand command) {
    return reportingPeriodCloseMutations.interimResultSweep(bookAccess, command);
  }

  @Override
  public ContractDecision<FiscalYearCloseResult> fiscalYearClose(
      BookAccess bookAccess, FiscalYearCloseCommand command) {
    return reportingPeriodCloseMutations.fiscalYearClose(bookAccess, command);
  }

  @Override
  public ContractDecision<LedgerPlanResult> executePlan(BookAccess bookAccess, LedgerPlan plan) {
    if (plan.steps().stream().anyMatch(step -> step.kind().mutatesBook())) {
      return SqliteCliWorkflowSessions.withPlanExecutionSessionDecision(
          openPlanExecutionSession(bookAccess),
          bookSession ->
              SqliteCliMutationAuthorization.withAttestationAuthorization(
                  bookAccess,
                  authorizer ->
                      ContractDecision.accepted(executePlan(bookSession, plan, authorizer))));
    }
    return SqliteCliWorkflowSessions.withPlanExecutionSession(
        openPlanExecutionSession(bookAccess),
        bookSession -> executePlan(bookSession, plan, READ_ONLY_PLAN_AUTHORIZER));
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
    return SqliteCliWorkflowSessions.withPostingSessionDecision(
        SqlitePostingSessions.openResolved(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                SqliteCliWorkflowSessions.postingApplicationService(
                                        bookSession, clock)
                                    .commit(command, authorizer))),
                () ->
                    new PostEntryResult.CommitRejected(
                        command.requestProvenance().idempotencyKey(),
                        new PostingRejection.BookNotInitialized())));
  }

  private ContractDecision<SqlitePlanExecutionSession> openPlanExecutionSession(
      BookAccess bookAccess) {
    return SqlitePlanExecutionSessions.openResolved(
        bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET);
  }

  private LedgerPlanResult executePlan(
      SqlitePlanExecutionSession bookSession,
      LedgerPlan plan,
      AttestationOperationAuthorizer authorizer) {
    return new LedgerPlanService(
            bookSession,
            new BookWorkflowExecutionDependencies(
                bookSession,
                bookSession,
                bookSession,
                bookSession,
                bookSession,
                bookSession,
                bookSession,
                new UuidV7PostingIdGenerator()),
            clock)
        .execute(plan, authorizer);
  }
}
