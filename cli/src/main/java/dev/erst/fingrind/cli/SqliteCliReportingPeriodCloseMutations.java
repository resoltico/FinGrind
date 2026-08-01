package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.FiscalYearCloseService;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqliteReportingPeriodCloseSessions;
import java.time.Clock;
import java.util.Objects;

/** Owns signed close operations through one initialized reporting-period-close session. */
final class SqliteCliReportingPeriodCloseMutations {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliReportingPeriodCloseMutations(
      Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  ContractDecision<InterimResultSweepResult> interimResultSweep(
      BookAccess bookAccess, InterimResultSweepCommand command) {
    return SqliteCliWorkflowSessions.withReportingPeriodCloseSessionDecision(
        SqliteReportingPeriodCloseSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                BookkeepingPublishedLanguageTranslator.toPublished(
                                    new InterimResultSweepService(
                                            bookSession,
                                            bookSession,
                                            new UuidV7PostingIdGenerator(),
                                            clock)
                                        .interimResultSweep(
                                            BookkeepingRequestPublishedLanguageTranslator
                                                .fromPublished(command),
                                            authorizer)))),
                () ->
                    new InterimResultSweepResult.Rejected(
                        new BookAdministrationRejection.BookNotInitialized())));
  }

  ContractDecision<FiscalYearCloseResult> fiscalYearClose(
      BookAccess bookAccess, FiscalYearCloseCommand command) {
    return SqliteCliWorkflowSessions.withReportingPeriodCloseSessionDecision(
        SqliteReportingPeriodCloseSessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                BookkeepingPublishedLanguageTranslator.toPublished(
                                    new FiscalYearCloseService(
                                            bookSession,
                                            bookSession,
                                            new UuidV7PostingIdGenerator(),
                                            clock)
                                        .fiscalYearClose(
                                            BookkeepingRequestPublishedLanguageTranslator
                                                .fromPublished(command),
                                            authorizer)))),
                () ->
                    new FiscalYearCloseResult.Rejected(
                        new BookAdministrationRejection.BookNotInitialized())));
  }
}
