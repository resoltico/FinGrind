package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared posting delegation defaults for SQLite capability wrappers. */
interface SqlitePostingCapabilityView extends SqlitePostingSession, SqliteReadCapabilityView {
  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord>
      findAccrualCutoff(dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId accrualCutoffId) {
    return SqliteReadCapabilityView.super.findAccrualCutoff(accrualCutoffId);
  }

  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.InventoryAccountState>
      findInventoryAccountState(dev.erst.fingrind.core.AccountCode inventoryAccountCode) {
    return SqliteReadCapabilityView.super.findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord>
      inventoryMovements(dev.erst.fingrind.core.PostingId postingId) {
    return SqliteReadCapabilityView.super.inventoryMovements(postingId);
  }

  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

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

  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }

  @Override
  default PostingCommitResult commit(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .commit(postingDraft, postingIdGenerator, attestationAuthorizer);
  }
}
