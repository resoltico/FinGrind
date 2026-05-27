package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import java.util.Objects;

/** Maps local bookkeeping-administration rejections into the published contract. */
final class BookkeepingAdministrationRejectionPublishedMapper {
  private BookkeepingAdministrationRejectionPublishedMapper() {}

  static BookAdministrationRejection toPublished(BookkeepingAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    if (rejection instanceof BookkeepingAdministrationRejection.BookAlreadyInitialized) {
      return new BookAdministrationRejection.BookAlreadyInitialized();
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookNotInitialized) {
      return new BookAdministrationRejection.BookNotInitialized();
    }
    if (rejection instanceof BookkeepingAdministrationRejection.BookContainsSchema) {
      return new BookAdministrationRejection.BookContainsSchema();
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.PeriodResultTransferMustStartAt conflict) {
      return new BookAdministrationRejection.PeriodResultTransferMustStartAt(
          conflict.requiredEffectiveDateFrom());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.PeriodResultTransferFutureDate conflict) {
      return new BookAdministrationRejection.PeriodResultTransferFutureDate(
          conflict.attemptedEffectiveDateTo());
    }
    if (rejection
        instanceof
        BookkeepingAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary conflict) {
      return new BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
          conflict.attemptedEffectiveDateFrom(),
          conflict.attemptedEffectiveDateTo(),
          conflict.fiscalYearStart());
    }
    return toPublishedAccountStructureRejection(rejection);
  }

  private static BookAdministrationRejection toPublishedAccountStructureRejection(
      BookkeepingAdministrationRejection rejection) {
    return switch (rejection) {
      case BookkeepingAdministrationRejection.AccountTypeConflict conflict ->
          new BookAdministrationRejection.AccountTypeConflict(
              conflict.accountCode(),
              conflict.existingAccountType(),
              conflict.requestedAccountType());
      case BookkeepingAdministrationRejection.AccountRoleConflict conflict ->
          new BookAdministrationRejection.AccountRoleConflict(
              conflict.accountCode(),
              conflict.existingAccountRole(),
              conflict.requestedAccountRole());
      case BookkeepingAdministrationRejection.AccountTaxonomyConflict conflict ->
          new BookAdministrationRejection.AccountTaxonomyConflict(
              conflict.accountCode(),
              conflict.existingAccountTaxonomy(),
              conflict.requestedAccountTaxonomy());
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
      case BookkeepingAdministrationRejection.ParentAccountRoleConflict conflict ->
          new BookAdministrationRejection.ParentAccountRoleConflict(
              conflict.accountCode(),
              conflict.requestedAccountRole(),
              conflict.parentAccountCode(),
              conflict.parentAccountRole());
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
      case BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing conflict ->
          new BookAdministrationRejection.ResultHoldingAccountCandidateMissing(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.inactiveCandidateAccountCodes());
      case BookkeepingAdministrationRejection.ResultHoldingAccountCandidateAmbiguous conflict ->
          new BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.candidateAccountCodes());
      default ->
          throw new IllegalStateException(
              "Unsupported administration rejection for account-structure mapping: "
                  + rejection.getClass().getName());
    };
  }
}
