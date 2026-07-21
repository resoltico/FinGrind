package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.time.Instant;
import java.util.List;

/** Shared administration delegation defaults for SQLite capability wrappers. */
interface SqliteAdministrationCapabilityView
    extends SqliteAdministrationSession,
        SqliteReadAccountCatalogCapabilityView,
        SqliteReadTaxCatalogCapabilityView {
  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  @Override
  default dev.erst.fingrind.executor.spi.BookLifecycleInspection inspectBook() {
    return SqliteReadAccountCatalogCapabilityView.super.inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    return SqliteReadAccountCatalogCapabilityView.super.allowsInitializedWorkflow();
  }

  @Override
  default dev.erst.fingrind.core.BookIdentity requireInitializedBookIdentity() {
    return SqliteReadAccountCatalogCapabilityView.super.requireInitializedBookIdentity();
  }

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

  @Override
  default AccountDeclarationOutcome declareAccount(
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().declareAccount(declaration, declaredAt, attestationAuthorizer);
  }

  @Override
  default dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome amendAccount(
      AccountDeclaration amendment,
      Instant amendedAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().amendAccount(amendment, amendedAt, attestationAuthorizer);
  }

  @Override
  default dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome retireAccount(
      dev.erst.fingrind.core.AccountCode accountCode,
      Instant retiredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().retireAccount(accountCode, retiredAt, attestationAuthorizer);
  }

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
