package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.TaxAdministrationService;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import java.time.Clock;
import java.util.Objects;

/** Owns administrative account and tax mutations through one initialized SQLite book session. */
final class SqliteCliAdministrationMutations {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;

  SqliteCliAdministrationMutations(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
  }

  ContractDecision<DeclareAccountResult> declareAccount(
      BookAccess bookAccess, DeclareAccountCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSessionDecision(
        openAdministrationSession(bookAccess),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                BookkeepingPublishedLanguageTranslator.toPublished(
                                    administrationService(bookSession)
                                        .declareAccount(
                                            BookkeepingRequestPublishedLanguageTranslator
                                                .fromPublished(command),
                                            authorizer)))),
                () ->
                    new DeclareAccountResult.Rejected(
                        new BookAdministrationRejection.BookNotInitialized())));
  }

  ContractDecision<AmendAccountResult> amendAccount(
      BookAccess bookAccess, AmendAccountCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSessionDecision(
        openAdministrationSession(bookAccess),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                AccountRegistryPublishedLanguageTranslator.toPublished(
                                    administrationService(bookSession)
                                        .amendAccount(
                                            AccountRegistryPublishedLanguageTranslator
                                                .fromPublished(command),
                                            authorizer)))),
                () ->
                    new AmendAccountResult.Rejected(
                        new BookAdministrationRejection.BookNotInitialized())));
  }

  ContractDecision<RetireAccountResult> retireAccount(
      BookAccess bookAccess, RetireAccountCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSessionDecision(
        openAdministrationSession(bookAccess),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                AccountRegistryPublishedLanguageTranslator.toPublished(
                                    administrationService(bookSession)
                                        .retireAccount(command.accountCode(), authorizer)))),
                () ->
                    new RetireAccountResult.Rejected(
                        new BookAdministrationRejection.BookNotInitialized())));
  }

  ContractDecision<DeclareTaxRegistrationResult> declareTaxRegistration(
      BookAccess bookAccess, DeclareTaxRegistrationCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSessionDecision(
        openAdministrationSession(bookAccess),
        bookSession ->
            SqliteCliMutationAuthorization.withInitializedBook(
                bookSession,
                () ->
                    SqliteCliMutationAuthorization.withAttestationAuthorization(
                        bookAccess,
                        authorizer ->
                            ContractDecision.accepted(
                                new TaxAdministrationService(
                                        bookSession, bookSession, bookSession, clock)
                                    .declareTaxRegistration(command, authorizer))),
                () ->
                    new DeclareTaxRegistrationResult.Rejected(
                        new TaxDeclarationRejection.BookNotInitialized())));
  }

  private ContractDecision<dev.erst.fingrind.sqlite.SqliteAdministrationSession>
      openAdministrationSession(BookAccess bookAccess) {
    return SqliteAdministrationSessions.openResolved(
        bookAccess,
        SqliteBookSessionMode.READ_WRITE_EXISTING,
        passphraseResolver,
        SqlitePassphraseIntent.EXISTING_SECRET);
  }

  private BookAdministrationService administrationService(
      dev.erst.fingrind.sqlite.SqliteAdministrationSession bookSession) {
    return new BookAdministrationService(bookSession, bookSession, bookSession, clock);
  }
}
