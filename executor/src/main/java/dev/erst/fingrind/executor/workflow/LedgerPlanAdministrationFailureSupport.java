package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejectionPublishedMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared mapping support for administration rejections carried through ledger-plan workflows. */
final class LedgerPlanAdministrationFailureSupport {
  private LedgerPlanAdministrationFailureSupport() {}

  static BookAdministrationRejection toPublished(BookkeepingAdministrationRejection rejection) {
    return BookkeepingAdministrationRejectionPublishedMapper.toPublished(rejection);
  }

  static List<BookWorkflowFact> facts(BookAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    if (rejection instanceof BookAdministrationRejection.BookAlreadyInitialized
        || rejection instanceof BookAdministrationRejection.BookNotInitialized
        || rejection instanceof BookAdministrationRejection.BookContainsSchema) {
      return List.of();
    }
    if (isPublishedCloseWindowRejection(rejection)) {
      return closeWindowFacts(rejection);
    }
    return accountStructureFacts(rejection);
  }

  private static boolean isPublishedCloseWindowRejection(Object rejection) {
    return rejection instanceof BookkeepingAdministrationRejection.InterimResultSweepMustStartAt
        || rejection instanceof BookkeepingAdministrationRejection.InterimResultSweepFutureDate
        || rejection
            instanceof
            BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary
        || rejection instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt
        || rejection instanceof BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt
        || rejection
            instanceof
            BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
        || rejection instanceof BookkeepingAdministrationRejection.FiscalYearCloseFutureDate
        || rejection
            instanceof BookkeepingAdministrationRejection.FiscalYearCloseRequiresGeneratedPostings
        || rejection instanceof BookAdministrationRejection.InterimResultSweepMustStartAt
        || rejection instanceof BookAdministrationRejection.InterimResultSweepFutureDate
        || rejection
            instanceof BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseMustStartAt
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseMustEndAt
        || rejection
            instanceof BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon
        || rejection instanceof BookAdministrationRejection.FiscalYearCloseFutureDate
        || rejection instanceof FiscalYearCloseRequiresGeneratedPostings;
  }

  private static List<BookWorkflowFact> closeWindowFacts(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.InterimResultSweepMustStartAt conflict ->
          List.of(
              BookWorkflowFact.text(
                  "requiredEffectiveDateFrom", conflict.requiredEffectiveDateFrom().toString()));
      case BookAdministrationRejection.InterimResultSweepFutureDate conflict ->
          List.of(
              BookWorkflowFact.text(
                  "attemptedEffectiveDateTo", conflict.attemptedEffectiveDateTo().toString()));
      case BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary conflict ->
          List.of(
              BookWorkflowFact.text(
                  "attemptedEffectiveDateFrom", conflict.attemptedEffectiveDateFrom().toString()),
              BookWorkflowFact.text(
                  "attemptedEffectiveDateTo", conflict.attemptedEffectiveDateTo().toString()),
              BookWorkflowFact.text("fiscalYearStart", conflict.fiscalYearStart().wireValue()));
      case BookAdministrationRejection.FiscalYearCloseMustStartAt conflict ->
          List.of(
              BookWorkflowFact.text(
                  "requiredEffectiveDateFrom", conflict.requiredEffectiveDateFrom().toString()));
      case BookAdministrationRejection.FiscalYearCloseMustEndAt conflict ->
          List.of(
              BookWorkflowFact.text(
                  "requiredEffectiveDateTo", conflict.requiredEffectiveDateTo().toString()));
      case BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon conflict ->
          List.of(
              BookWorkflowFact.text(
                  "attemptedEffectiveDateTo", conflict.attemptedEffectiveDateTo().toString()),
              BookWorkflowFact.text(
                  "transferredThroughEffectiveDate",
                  conflict.transferredThroughEffectiveDate().toString()));
      case BookAdministrationRejection.FiscalYearCloseFutureDate conflict ->
          List.of(
              BookWorkflowFact.text(
                  "attemptedEffectiveDateTo", conflict.attemptedEffectiveDateTo().toString()));
      case FiscalYearCloseRequiresGeneratedPostings _ -> List.of();
      default ->
          throw new IllegalStateException(
              "Unsupported published close-window rejection: " + rejection.getClass().getName());
    };
  }

  private static List<BookWorkflowFact> accountStructureFacts(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.AccountTypeConflict conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text(
                  "existingAccountType", conflict.existingAccountType().wireValue()),
              BookWorkflowFact.text(
                  "requestedAccountType", conflict.requestedAccountType().wireValue()));
      case BookAdministrationRejection.AccountTaxonomyConflict conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.group(
                  "existingAccountTaxonomy",
                  accountTaxonomyFacts(conflict.existingAccountTaxonomy())),
              BookWorkflowFact.group(
                  "requestedAccountTaxonomy",
                  accountTaxonomyFacts(conflict.requestedAccountTaxonomy())));
      case dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("contraOfAccountCode", conflict.contraOfAccountCode().value()),
              BookWorkflowFact.text("violation", conflict.violation().wireValue()));
      case BookAdministrationRejection.ParentAccountMissing conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()));
      case BookAdministrationRejection.ParentAccountInactive conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()));
      case BookAdministrationRejection.ParentAccountTypeConflict conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text(
                  "requestedAccountType", conflict.requestedAccountType().wireValue()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()),
              BookWorkflowFact.text("parentAccountType", conflict.parentAccountType().wireValue()));
      case BookAdministrationRejection.ParentAccountNotHeader conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()),
              BookWorkflowFact.text(
                  "parentAccountNodeKind", conflict.parentAccountNodeKind().wireValue()));
      case BookAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()),
              BookWorkflowFact.group(
                  "requestedAccountTaxonomy",
                  accountTaxonomyFacts(conflict.requestedAccountTaxonomy())),
              BookWorkflowFact.group(
                  "parentAccountTaxonomy", accountTaxonomyFacts(conflict.parentAccountTaxonomy())));
      case BookAdministrationRejection.AccountHierarchyCycle conflict ->
          List.of(
              BookWorkflowFact.text("accountCode", conflict.accountCode().value()),
              BookWorkflowFact.text("parentAccountCode", conflict.parentAccountCode().value()));
      case CloseTargetAccountCandidateMissing conflict ->
          List.of(
              BookWorkflowFact.text(
                  "requiredFinancialPositionLineClassification",
                  conflict.requiredFinancialPositionLineClassification().wireValue()),
              BookWorkflowFact.count(
                  "inactiveCandidateAccountCount",
                  conflict.inactiveCandidateAccountCodes().size()));
      case CloseTargetAccountCandidateAmbiguous conflict ->
          List.of(
              BookWorkflowFact.text(
                  "requiredFinancialPositionLineClassification",
                  conflict.requiredFinancialPositionLineClassification().wireValue()),
              BookWorkflowFact.count(
                  "candidateAccountCount", conflict.candidateAccountCodes().size()));
      default ->
          throw new IllegalStateException(
              "Unsupported published account-structure rejection: "
                  + rejection.getClass().getName());
    };
  }

  private static List<BookWorkflowFact> accountTaxonomyFacts(AccountTaxonomy accountTaxonomy) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("accountNodeKind", accountTaxonomy.nodeKind().wireValue()));
    accountTaxonomy
        .parentAccountCode()
        .ifPresent(
            accountCode ->
                facts.add(BookWorkflowFact.text("parentAccountCode", accountCode.value())));
    accountTaxonomy
        .contraOfAccountCode()
        .ifPresent(
            accountCode ->
                facts.add(BookWorkflowFact.text("contraOfAccountCode", accountCode.value())));
    accountTaxonomy
        .financialPositionLineClassification()
        .ifPresent(
            classification ->
                facts.add(
                    BookWorkflowFact.text(
                        "financialPositionLineClassification", classification.wireValue())));
    accountTaxonomy
        .profitAndLossLineClassification()
        .ifPresent(
            classification ->
                facts.add(
                    BookWorkflowFact.text(
                        "profitAndLossLineClassification", classification.wireValue())));
    return List.copyOf(facts);
  }
}
