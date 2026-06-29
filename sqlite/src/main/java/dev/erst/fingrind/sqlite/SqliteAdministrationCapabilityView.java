package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
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
  default BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().openBook(initializedAt, bookIdentity, seededAccounts);
  }

  @Override
  default AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .declareAccount(accountCode, accountName, accountType, accountTaxonomy, declaredAt);
  }

  @Override
  default DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command, Instant declaredAt) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().declareTaxRegistration(command, declaredAt);
  }
}
