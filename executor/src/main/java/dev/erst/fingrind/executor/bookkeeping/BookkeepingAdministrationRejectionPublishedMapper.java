package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import java.util.Objects;

/** Maps local bookkeeping-administration rejections into the published contract. */
public final class BookkeepingAdministrationRejectionPublishedMapper {
  private BookkeepingAdministrationRejectionPublishedMapper() {}

  /** Translates one local bookkeeping-administration rejection into the published contract. */
  public static BookAdministrationRejection toPublished(
      BookkeepingAdministrationRejection rejection) {
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
        instanceof BookkeepingAdministrationRejection.InterimResultSweepMustStartAt conflict) {
      return new BookAdministrationRejection.InterimResultSweepMustStartAt(
          conflict.requiredEffectiveDateFrom());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.InterimResultSweepFutureDate conflict) {
      return new BookAdministrationRejection.InterimResultSweepFutureDate(
          conflict.attemptedEffectiveDateTo());
    }
    if (rejection
        instanceof
        BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary conflict) {
      return new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
          conflict.attemptedEffectiveDateFrom(),
          conflict.attemptedEffectiveDateTo(),
          conflict.fiscalYearStart());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt conflict) {
      return new BookAdministrationRejection.FiscalYearCloseMustStartAt(
          conflict.requiredEffectiveDateFrom());
    }
    if (rejection instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt conflict) {
      return new BookAdministrationRejection.FiscalYearCloseMustEndAt(
          conflict.requiredEffectiveDateTo());
    }
    if (rejection
        instanceof
        BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
            conflict) {
      return new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
          conflict.attemptedEffectiveDateTo(), conflict.transferredThroughEffectiveDate());
    }
    if (rejection
        instanceof BookkeepingAdministrationRejection.FiscalYearCloseFutureDate conflict) {
      return new BookAdministrationRejection.FiscalYearCloseFutureDate(
          conflict.attemptedEffectiveDateTo());
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
      case CloseTargetAccountCandidateMissing conflict ->
          new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.inactiveCandidateAccountCodes());
      case CloseTargetAccountCandidateAmbiguous conflict ->
          new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.candidateAccountCodes());
      default ->
          throw new IllegalStateException(
              "Unsupported administration rejection for account-structure mapping: "
                  + rejection.getClass().getName());
    };
  }
}
