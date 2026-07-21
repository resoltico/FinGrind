package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.Instant;
import java.util.List;

/** Attested book-opening, account-administration, and tax-registration delegation defaults. */
interface SqliteAttestedAdministrationMutationView
    extends BookAdministrationStore, TaxAdministrationStore {
  /** Returns the thread-ownership guard for the underlying SQLite store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  /** Initializes a previously unopened protected book with self-authorizing genesis evidence. */
  @Override
  default BookOpeningOutcome openAttestedBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<AccountDeclaration> seededAccounts,
      AttestationEvidence genesisEvidence) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .openAttestedBook(initializedAt, bookIdentity, seededAccounts, genesisEvidence);
  }

  /** Declares a new account in the protected book. */
  @Override
  default AccountDeclarationOutcome declareAccount(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().declareAccount(declaration, declaredAt, attestationAuthorizer);
  }

  /** Amends an existing account in the protected book. */
  @Override
  default AccountAmendmentOutcome amendAccount(
      AccountDeclaration amendment,
      Instant amendedAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().amendAccount(amendment, amendedAt, attestationAuthorizer);
  }

  /** Retires an account in the protected book. */
  @Override
  default AccountRetirementOutcome retireAccount(
      AccountCode accountCode,
      Instant retiredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().retireAccount(accountCode, retiredAt, attestationAuthorizer);
  }

  /** Declares one tax registration in the protected book. */
  @Override
  default DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .declareTaxRegistration(command, declaredAt, attestationAuthorizer);
  }
}
