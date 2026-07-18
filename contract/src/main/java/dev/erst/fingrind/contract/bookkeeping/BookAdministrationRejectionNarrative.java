package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.stream.Collectors;

/** Administration-rejection narrative catalog split out from the public rejection facade. */
final class BookAdministrationRejectionNarrative {
  private BookAdministrationRejectionNarrative() {}

  static String message(BookAdministrationRejection rejection) {
    if (isLifecycleRejection(rejection)) {
      return lifecycleMessage(rejection);
    }
    if (isCloseWindowRejection(rejection)) {
      return closeWindowMessage(rejection);
    }
    return accountCatalogMessage(rejection);
  }

  private static boolean isLifecycleRejection(BookAdministrationRejection rejection) {
    return rejection instanceof BookAdministrationRejection.BookAlreadyInitialized
        || rejection instanceof BookAdministrationRejection.BookNotInitialized
        || rejection instanceof BookAdministrationRejection.BookContainsSchema;
  }

  private static boolean isCloseWindowRejection(BookAdministrationRejection rejection) {
    return rejection instanceof CloseTargetAccountCandidateMissing
        || rejection instanceof CloseTargetAccountCandidateAmbiguous
        || rejection instanceof BookAdministrationRejection.InterimResultSweepMustStartAt
        || rejection instanceof BookAdministrationRejection.InterimResultSweepFutureDate
        || rejection
            instanceof BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseMustStartAt
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseMustEndAt
        || rejection
            instanceof BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseFutureDate;
  }

  static String lifecycleMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          "The selected book is already initialized.";
      case BookAdministrationRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + RejectionNarrative.openBookOperation()
              + ".";
      case BookAdministrationRejection.BookContainsSchema _ ->
          "The selected SQLite file already contains schema objects and cannot be initialized as a new book.";
      default -> throw new IllegalStateException("Unsupported lifecycle rejection: " + rejection);
    };
  }

  static String accountCatalogMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.AccountTypeConflict accountTypeConflict ->
          "Account '%s' already exists with account type '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  accountTypeConflict.accountCode().value(),
                  accountTypeConflict.existingAccountType().wireValue(),
                  accountTypeConflict.requestedAccountType().wireValue());
      case BookAdministrationRejection.AccountTaxonomyConflict accountTaxonomyConflict ->
          "Account '%s' already exists with a different immutable hierarchy or statement taxonomy."
              .formatted(accountTaxonomyConflict.accountCode().value());
      case dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid conflict ->
          "Account '%s' cannot reduce account '%s' because the contra relationship is %s."
              .formatted(
                  conflict.accountCode().value(),
                  conflict.contraOfAccountCode().value(),
                  conflict.violation().wireValue());
      case AccountRegistryLifecycleRejection.AccountNotFound missing ->
          "Account '%s' is not declared in this book.".formatted(missing.accountCode().value());
      case AccountRegistryLifecycleRejection.AccountHasDependents dependents ->
          "Account '%s' cannot change lifecycle while durable dependents remain: %s."
              .formatted(
                  dependents.accountCode().value(),
                  dependents.dependencies().stream()
                      .map(dependency -> dependency.wireValue())
                      .collect(Collectors.joining(", ")));
      case AccountRegistryLifecycleRejection.AccountBalanceNotZero balance ->
          "Account '%s' cannot retire because its current balance is not zero."
              .formatted(balance.accountCode().value());
      case BookAdministrationRejection.ParentAccountMissing rejectionMissing ->
          "Account '%s' names parent account '%s', but that parent account is not declared in this book."
              .formatted(
                  rejectionMissing.accountCode().value(),
                  rejectionMissing.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountInactive rejectionInactive ->
          "Account '%s' names parent account '%s', but that parent account is inactive."
              .formatted(
                  rejectionInactive.accountCode().value(),
                  rejectionInactive.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountTypeConflict rejectionTypeConflict ->
          "Account '%s' is declared as '%s', but parent account '%s' is '%s'. Parent and child must share one account type."
              .formatted(
                  rejectionTypeConflict.accountCode().value(),
                  rejectionTypeConflict.requestedAccountType().wireValue(),
                  rejectionTypeConflict.parentAccountCode().value(),
                  rejectionTypeConflict.parentAccountType().wireValue());
      case BookAdministrationRejection.ParentAccountNotHeader rejectionNotHeader ->
          "Account '%s' names parent account '%s', but that parent is declared as '%s' and cannot own child accounts."
              .formatted(
                  rejectionNotHeader.accountCode().value(),
                  rejectionNotHeader.parentAccountCode().value(),
                  rejectionNotHeader.parentAccountNodeKind().wireValue());
      case BookAdministrationRejection.ParentAccountTaxonomyConflict rejectionTaxonomyConflict ->
          "Account '%s' names parent account '%s', but the child and parent do not share one statement-classification family."
              .formatted(
                  rejectionTaxonomyConflict.accountCode().value(),
                  rejectionTaxonomyConflict.parentAccountCode().value());
      case BookAdministrationRejection.AccountHierarchyCycle rejectionCycle ->
          "Account '%s' cannot name parent account '%s' because that relationship would create a chart hierarchy cycle."
              .formatted(
                  rejectionCycle.accountCode().value(), rejectionCycle.parentAccountCode().value());
      default ->
          throw new IllegalStateException("Unsupported account-catalog rejection: " + rejection);
    };
  }

  static String closeWindowMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case CloseTargetAccountCandidateMissing rejectionMissing ->
          rejectionMissing.inactiveCandidateAccountCodes().isEmpty()
              ? "No active declared close target satisfies required classification '%s'."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue())
              : "No active declared close target satisfies required classification '%s'; inactive candidates: %s."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue(),
                      rejectionMissing.inactiveCandidateAccountCodes().stream()
                          .map(AccountCode::value)
                          .collect(Collectors.joining(", ")));
      case CloseTargetAccountCandidateAmbiguous rejectionAmbiguous ->
          "More than one active declared close target satisfies required classification '%s': %s."
              .formatted(
                  rejectionAmbiguous.requiredFinancialPositionLineClassification().wireValue(),
                  rejectionAmbiguous.candidateAccountCodes().stream()
                      .map(AccountCode::value)
                      .collect(Collectors.joining(", ")));
      case BookAdministrationRejection.InterimResultSweepMustStartAt rejectionStartAt ->
          "Interim result sweep must start at '%s' to preserve one contiguous sweep horizon."
              .formatted(rejectionStartAt.requiredEffectiveDateFrom());
      case BookAdministrationRejection.InterimResultSweepFutureDate rejectionFutureDate ->
          "Interim result sweep cannot end after the current UTC date; requested '%s'."
              .formatted(rejectionFutureDate.attemptedEffectiveDateTo());
      case BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary
              rejectionBoundary ->
          "Interim result sweep '%s' through '%s' crosses this book's fiscal-year boundary '%s'."
              .formatted(
                  rejectionBoundary.attemptedEffectiveDateFrom(),
                  rejectionBoundary.attemptedEffectiveDateTo(),
                  rejectionBoundary.fiscalYearStart().wireValue());
      case BookAdministrationRejection.FiscalYearCloseMustStartAt rejectionStartAt ->
          "Fiscal-year close must start at '%s' to cover the admissible fiscal-year segment."
              .formatted(rejectionStartAt.requiredEffectiveDateFrom());
      case BookAdministrationRejection.FiscalYearCloseMustEndAt rejectionEndAt ->
          "Fiscal-year close must end at '%s' to cover one full fiscal year."
              .formatted(rejectionEndAt.requiredEffectiveDateTo());
      case BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
              rejectionHorizon ->
          "Fiscal-year close ending '%s' precedes the live transferred-through horizon '%s'."
              .formatted(
                  rejectionHorizon.attemptedEffectiveDateTo(),
                  rejectionHorizon.transferredThroughEffectiveDate());
      case BookAdministrationRejection.FiscalYearCloseFutureDate rejectionFutureDate ->
          "Fiscal-year close cannot end after the current UTC date; requested '%s'."
              .formatted(rejectionFutureDate.attemptedEffectiveDateTo());
      default ->
          throw new IllegalStateException("Unsupported close-window rejection: " + rejection);
    };
  }
}
