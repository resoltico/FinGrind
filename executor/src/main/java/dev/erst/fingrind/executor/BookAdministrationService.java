package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.ChartOfAccounts;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookLifecycleReader lifecycleReader;
  private final BookAdministrationStore bookStore;
  private final AccountCatalogStore accountCatalogStore;
  private final Clock clock;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(
      BookLifecycleReader lifecycleReader,
      BookAdministrationStore bookStore,
      AccountCatalogStore accountCatalogStore,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.accountCatalogStore = Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Explicitly initializes one protected book with a signed genesis operation. */
  public BookOpeningOutcome openAttestedBook(
      BookIdentity bookIdentity, AttestationEvidence genesisEvidence) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(genesisEvidence, "genesisEvidence");
    return bookStore.openAttestedBook(
        clock.instant(),
        bookIdentity,
        BookTemplateAccounts.declarations(bookIdentity.bookDoctrine()),
        genesisEvidence);
  }

  /** Declares or reactivates one account in the selected book. */
  public AccountDeclarationOutcome declareAccount(
      AccountDeclaration command, AttestationOperationAuthorizer attestationAuthorizer) {
    return requireImmediateAttestation(declareAccountInternal(command, attestationAuthorizer));
  }

  private AccountDeclarationOutcome declareAccountInternal(
      AccountDeclaration command, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    Optional<BookkeepingAdministrationRejection> rejection =
        ChartOfAccounts.of(accountCatalogStore.allAccounts()).validate(command);
    if (rejection.isPresent()) {
      return new AccountDeclarationOutcome.Rejected(rejection.orElseThrow());
    }
    return bookStore.declareAccount(command, clock.instant(), attestationAuthorizer);
  }

  /** Amends one never-posted and unreferenced account definition. */
  public AccountAmendmentOutcome amendAccount(
      AccountDeclaration command, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new AccountAmendmentOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    Optional<BookkeepingAdministrationRejection> rejection =
        ChartOfAccounts.of(accountCatalogStore.allAccounts()).validate(command);
    if (rejection.isPresent()) {
      return new AccountAmendmentOutcome.Rejected(rejection.orElseThrow());
    }
    return requireImmediateAttestation(
        bookStore.amendAccount(command, clock.instant(), attestationAuthorizer));
  }

  /** Retires one zero-balance account from ordinary authored posting use. */
  public AccountRetirementOutcome retireAccount(
      dev.erst.fingrind.core.AccountCode accountCode,
      AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(accountCode, "accountCode");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new AccountRetirementOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return requireImmediateAttestation(
        bookStore.retireAccount(accountCode, clock.instant(), attestationAuthorizer));
  }

  private static AccountDeclarationOutcome requireImmediateAttestation(
      AccountDeclarationOutcome outcome) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case AccountDeclarationOutcome.Declared declared -> {
        requireImmediate(declared.attestationAppend());
        yield declared;
      }
      case AccountDeclarationOutcome.Reactivated reactivated -> {
        requireImmediate(reactivated.attestationAppend());
        yield reactivated;
      }
      case AccountDeclarationOutcome.Renamed renamed -> {
        requireImmediate(renamed.attestationAppend());
        yield renamed;
      }
      case AccountDeclarationOutcome.Unchanged unchanged -> unchanged;
      case AccountDeclarationOutcome.Rejected rejected -> rejected;
    };
  }

  private static AccountAmendmentOutcome requireImmediateAttestation(
      AccountAmendmentOutcome outcome) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case AccountAmendmentOutcome.Amended amended -> {
        requireImmediate(amended.attestationAppend());
        yield amended;
      }
      case AccountAmendmentOutcome.Unchanged unchanged -> unchanged;
      case AccountAmendmentOutcome.Rejected rejected -> rejected;
    };
  }

  private static AccountRetirementOutcome requireImmediateAttestation(
      AccountRetirementOutcome outcome) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case AccountRetirementOutcome.Retired retired -> {
        requireImmediate(retired.attestationAppend());
        yield retired;
      }
      case AccountRetirementOutcome.Unchanged unchanged -> unchanged;
      case AccountRetirementOutcome.Rejected rejected -> rejected;
    };
  }

  private static void requireImmediate(AttestationAppendOutcome.Appended attestationAppend) {
    Objects.requireNonNull(attestationAppend, "attestationAppend").requireAppended();
  }
}
