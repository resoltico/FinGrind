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
    if (isTransferHorizonRejection(rejection)) {
      return transferHorizonMessage(rejection);
    }
    return accountCatalogMessage(rejection);
  }

  private static boolean isLifecycleRejection(BookAdministrationRejection rejection) {
    return rejection instanceof BookAdministrationRejection.BookAlreadyInitialized
        || rejection instanceof BookAdministrationRejection.BookNotInitialized
        || rejection instanceof BookAdministrationRejection.BookContainsSchema;
  }

  private static boolean isTransferHorizonRejection(BookAdministrationRejection rejection) {
    return rejection instanceof BookAdministrationRejection.ResultHoldingAccountCandidateMissing
        || rejection instanceof BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous
        || rejection instanceof BookAdministrationRejection.PeriodResultTransferMustStartAt
        || rejection instanceof BookAdministrationRejection.PeriodResultTransferFutureDate
        || rejection
            instanceof BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary;
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
      case BookAdministrationRejection.AccountRoleConflict accountRoleConflict ->
          "Account '%s' already exists with account role '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  accountRoleConflict.accountCode().value(),
                  accountRoleConflict.existingAccountRole().wireValue(),
                  accountRoleConflict.requestedAccountRole().wireValue());
      case BookAdministrationRejection.AccountTaxonomyConflict accountTaxonomyConflict ->
          "Account '%s' already exists with a different immutable hierarchy or statement taxonomy."
              .formatted(accountTaxonomyConflict.accountCode().value());
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
      case BookAdministrationRejection.ParentAccountRoleConflict rejectionRoleConflict ->
          "Account '%s' is declared with role '%s', but parent account '%s' uses role '%s'. Parent and child must share one account role."
              .formatted(
                  rejectionRoleConflict.accountCode().value(),
                  rejectionRoleConflict.requestedAccountRole().wireValue(),
                  rejectionRoleConflict.parentAccountCode().value(),
                  rejectionRoleConflict.parentAccountRole().wireValue());
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

  static String transferHorizonMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.ResultHoldingAccountCandidateMissing rejectionMissing ->
          rejectionMissing.inactiveCandidateAccountCodes().isEmpty()
              ? "No active declared result-holding account satisfies required classification '%s'."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue())
              : "No active declared result-holding account satisfies required classification '%s'; inactive candidates: %s."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue(),
                      rejectionMissing.inactiveCandidateAccountCodes().stream()
                          .map(AccountCode::value)
                          .collect(Collectors.joining(", ")));
      case BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous rejectionAmbiguous ->
          "More than one active declared result-holding account satisfies required classification '%s': %s."
              .formatted(
                  rejectionAmbiguous.requiredFinancialPositionLineClassification().wireValue(),
                  rejectionAmbiguous.candidateAccountCodes().stream()
                      .map(AccountCode::value)
                      .collect(Collectors.joining(", ")));
      case BookAdministrationRejection.PeriodResultTransferMustStartAt rejectionStartAt ->
          "Period result transfer must start at '%s' to preserve one contiguous transfer horizon."
              .formatted(rejectionStartAt.requiredEffectiveDateFrom());
      case BookAdministrationRejection.PeriodResultTransferFutureDate rejectionFutureDate ->
          "Period result transfer cannot end after the current UTC date; requested '%s'."
              .formatted(rejectionFutureDate.attemptedEffectiveDateTo());
      case BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary
              rejectionBoundary ->
          "Period result transfer '%s' through '%s' crosses this book's fiscal-year boundary '%s'."
              .formatted(
                  rejectionBoundary.attemptedEffectiveDateFrom(),
                  rejectionBoundary.attemptedEffectiveDateTo(),
                  rejectionBoundary.fiscalYearStart().wireValue());
      default ->
          throw new IllegalStateException("Unsupported transfer-horizon rejection: " + rejection);
    };
  }
}
