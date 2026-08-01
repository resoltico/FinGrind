package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;

/** Maps Account Registry rejections into the published administration contract. */
final class AccountRegistryRejectionPublishedMapper {
  private AccountRegistryRejectionPublishedMapper() {}

  static BookAdministrationRejection toPublished(BookkeepingAdministrationRejection rejection) {
    return switch (rejection) {
      case BookkeepingAdministrationRejection.AccountTypeConflict conflict ->
          new BookAdministrationRejection.AccountTypeConflict(
              conflict.accountCode(),
              conflict.existingAccountType(),
              conflict.requestedAccountType());
      case BookkeepingAdministrationRejection.AccountTaxonomyConflict conflict ->
          new BookAdministrationRejection.AccountTaxonomyConflict(
              conflict.accountCode(),
              conflict.existingAccountTaxonomy(),
              conflict.requestedAccountTaxonomy());
      case ContraAccountInvalid conflict ->
          new dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid(
              conflict.accountCode(), conflict.contraOfAccountCode(), conflict.violation());
      case AccountRegistryLifecycleRejection.AccountNotFound missing ->
          new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
              .AccountNotFound(missing.accountCode());
      case AccountRegistryLifecycleRejection.AccountHasDependents dependents ->
          new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
              .AccountHasDependents(dependents.accountCode(), dependents.dependencies());
      case AccountRegistryLifecycleRejection.AccountBalanceNotZero balance ->
          new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
              .AccountBalanceNotZero(balance.accountCode());
      case BookkeepingAdministrationRejection.ParentAccountMissing conflict ->
          new BookAdministrationRejection.ParentAccountMissing(
              conflict.accountCode(), conflict.parentAccountCode());
      case BookkeepingAdministrationRejection.ParentAccountInactive conflict ->
          new BookAdministrationRejection.ParentAccountInactive(
              conflict.accountCode(), conflict.parentAccountCode());
      case BookkeepingAdministrationRejection.ParentAccountTypeConflict conflict ->
          new BookAdministrationRejection.ParentAccountTypeConflict(
              conflict.accountCode(),
              conflict.requestedAccountType(),
              conflict.parentAccountCode(),
              conflict.parentAccountType());
      case BookkeepingAdministrationRejection.ParentAccountNotHeader conflict ->
          new BookAdministrationRejection.ParentAccountNotHeader(
              conflict.accountCode(),
              conflict.parentAccountCode(),
              conflict.parentAccountNodeKind());
      case BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          new BookAdministrationRejection.ParentAccountTaxonomyConflict(
              conflict.accountCode(),
              conflict.requestedAccountTaxonomy(),
              conflict.parentAccountCode(),
              conflict.parentAccountTaxonomy());
      case BookkeepingAdministrationRejection.AccountHierarchyCycle conflict ->
          new BookAdministrationRejection.AccountHierarchyCycle(
              conflict.accountCode(), conflict.parentAccountCode());
      default ->
          throw new IllegalArgumentException(
              "Expected an Account Registry rejection but received "
                  + rejection.getClass().getName()
                  + ".");
    };
  }
}
