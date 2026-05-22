package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Canonical human-readable rejection prose for public rejection contracts. */
public final class RejectionNarrative {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);

  private RejectionNarrative() {}

  /** Returns the canonical human-readable message for an administration rejection. */
  public static String message(BookAdministrationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          "The selected book is already initialized.";
      case BookAdministrationRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case BookAdministrationRejection.BookContainsSchema _ ->
          "The selected SQLite file already contains schema objects and cannot be initialized as a new book.";
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
      case BookAdministrationRejection.ClosingEquityAccountCandidateMissing rejectionMissing ->
          rejectionMissing.inactiveCandidateAccountCodes().isEmpty()
              ? "No active declared closing-equity account satisfies required classification '%s'."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue())
              : "No active declared closing-equity account satisfies required classification '%s'; inactive candidates: %s."
                  .formatted(
                      rejectionMissing.requiredFinancialPositionLineClassification().wireValue(),
                      rejectionMissing.inactiveCandidateAccountCodes().stream()
                          .map(AccountCode::value)
                          .collect(java.util.stream.Collectors.joining(", ")));
      case BookAdministrationRejection.ClosingEquityAccountCandidateAmbiguous rejectionAmbiguous ->
          "More than one active declared closing-equity account satisfies required classification '%s': %s."
              .formatted(
                  rejectionAmbiguous.requiredFinancialPositionLineClassification().wireValue(),
                  rejectionAmbiguous.candidateAccountCodes().stream()
                      .map(AccountCode::value)
                      .collect(java.util.stream.Collectors.joining(", ")));
      case BookAdministrationRejection.PeriodCloseMustStartAt rejectionStartAt ->
          "Close period must start at '%s' to preserve one contiguous close horizon."
              .formatted(rejectionStartAt.requiredEffectiveDateFrom());
      case BookAdministrationRejection.PeriodCloseFutureDate rejectionFutureDate ->
          "Close period cannot end after the current UTC date; requested '%s'."
              .formatted(rejectionFutureDate.attemptedEffectiveDateTo());
      case BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary rejectionBoundary ->
          "Close period '%s' through '%s' crosses this book's fiscal-year boundary '%s'."
              .formatted(
                  rejectionBoundary.attemptedEffectiveDateFrom(),
                  rejectionBoundary.attemptedEffectiveDateTo(),
                  rejectionBoundary.fiscalYearStart().wireValue());
    };
  }

  /** Returns the canonical human-readable message for a maintenance rejection. */
  public static String message(BookMaintenanceRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          "Book '%s' has blocking sibling artifacts and is not safe for one closed-copy maintenance workflow."
              .formatted(blockingArtifacts.bookFilePath().value());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          "Backup source '%s' has blocking sibling artifacts and is not safe to restore from."
              .formatted(blockingArtifacts.backupFilePath().value());
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          "Backup source '%s' matches live book '%s'; FinGrind will not restore a book from itself."
              .formatted(
                  sourceMatchesLiveBook.backupFilePath().value(),
                  sourceMatchesLiveBook.bookFilePath().value());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          "Protected-book artifact '%s' with role '%s' is actively in use and cannot be maintained safely."
              .formatted(
                  artifactBusy.artifactPath().value(), artifactBusy.artifactRole().wireValue());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          "Backup destination '%s' already exists and FinGrind will not overwrite it."
              .formatted(destinationAlreadyExists.backupFilePath().value());
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists destinationAlreadyExists ->
          "Backup key file '%s' already exists and FinGrind will not overwrite it."
              .formatted(destinationAlreadyExists.backupBookKeyFilePath().value());
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          "Protected-book artifact '%s' with role '%s' failed verification: '%s'."
              .formatted(
                  verificationFailed.artifactPath().value(),
                  verificationFailed.artifactRole().wireValue(),
                  verificationFailed.verificationFailure().wireValue());
      case BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          "No sibling rekey rollback artifacts exist beside '%s'."
              .formatted(noRollbackArtifactsFound.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          "More than one sibling rekey rollback artifact exists beside '%s'; choose one explicit rollback artifact path."
              .formatted(selectionRequired.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactNotFound rollbackArtifactNotFound ->
          "Rollback artifact '%s' does not exist."
              .formatted(rollbackArtifactNotFound.rollbackArtifactPath().value());
      case BookMaintenanceRejection.RollbackArtifactNotForBook rollbackArtifactNotForBook ->
          "Rollback artifact '%s' does not belong to book '%s'."
              .formatted(
                  rollbackArtifactNotForBook.rollbackArtifactPath().value(),
                  rollbackArtifactNotForBook.bookFilePath().value());
    };
  }

  /** Returns the canonical human-readable message for a query rejection. */
  public static String message(BookQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookQueryRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case BookQueryRejection.UnknownAccount unknownAccount ->
          "Account '%s' is not declared in this book."
              .formatted(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          "Posting '%s' does not exist in this book."
              .formatted(postingNotFound.postingId().value());
    };
  }

  /** Returns the canonical human-readable message for a posting rejection. */
  public static String message(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case PostingRejection.AccountStateViolations violations ->
          "Posting references undeclared, inactive, or non-postable accounts."
              + " Fix every issue in details.violations before retrying."
              + " Reported issues: "
              + violations.violations().size();
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "A posting with the same idempotency key already exists in this book.";
      case PostingRejection.BookFunctionalCurrencyMismatch functionalCurrencyMismatch ->
          "Posting currency '%s' does not match this book's functional currency '%s'."
              .formatted(
                  functionalCurrencyMismatch.attemptedCurrency().code(),
                  functionalCurrencyMismatch.functionalCurrency().code());
      case PostingRejection.ClosedPeriodViolation rejectionClosedPeriod ->
          "Posting effective date '%s' falls inside the closed-through horizon ending '%s'."
              .formatted(
                  rejectionClosedPeriod.attemptedEffectiveDate(),
                  rejectionClosedPeriod.closedThroughEffectiveDate());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening-balance postings are allowed only before the first committed posting in this book; the first blocking posting is '%s' on '%s'."
              .formatted(
                  rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
                  rejectionWindowClosed.firstBlockingEffectiveDate());
      case PostingRejection.OpeningBalanceTouchesNominalAccount openingBalanceNominal ->
          "Opening-balance postings may seed only asset, liability, or equity accounts; '%s' is '%s'."
              .formatted(
                  openingBalanceNominal.accountCode().value(),
                  openingBalanceNominal.accountType().wireValue());
      case PostingRejection.ClosingEquityAccountReserved rejectionReserved ->
          "Closing-equity account '%s' is reserved for generated period-close postings."
              .formatted(rejectionReserved.accountCode().value());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          "No committed posting exists for reversal target '%s'."
              .formatted(reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          "Posting '%s' already has a full reversal."
              .formatted(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          "Reversal candidate does not negate posting '%s'."
              .formatted(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }
}
