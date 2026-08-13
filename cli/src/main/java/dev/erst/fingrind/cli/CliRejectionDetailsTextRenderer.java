package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliBookLifecycleRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliCloseRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliPostingRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliQueryPlanRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels;
import java.util.List;

/** Renders every closed CLI rejection-detail family through its owning text policy. */
final class CliRejectionDetailsTextRenderer {
  private CliRejectionDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows, CliRejectionJsonModels.RejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliPostingRejectionJsonModels.PostingRejectionDetails details ->
          CliPostingRejectionTextRenderer.appendRows(rows, details);
      case CliAccountRejectionJsonModels.AccountRejectionDetails details ->
          appendAccountRows(rows, details);
      case CliCloseRejectionJsonModels.CloseWindowRejectionDetails details ->
          appendCloseWindowRows(rows, details);
      case CliQueryPlanRejectionJsonModels.QueryOrPlanRejectionDetails details ->
          appendQueryOrPlanRows(rows, details);
      case CliTaxRejectionJsonModels.TaxRejectionDetails details -> appendTaxRows(rows, details);
      case CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails details ->
          CliMaintenanceFailureOutputRenderer.appendRows(rows, details);
      case CliBookLifecycleRejectionJsonModels.BackupAcknowledgementAuthorizationRejectedDetails
              details ->
          appendBackupAcknowledgementAuthorizationRejectedRows(rows, details);
      case CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails details ->
          appendAttestationReviewRequiredRows(rows, details);
    }
  }

  private static void appendBackupAcknowledgementAuthorizationRejectedRows(
      List<List<String>> rows,
      CliBookLifecycleRejectionJsonModels.BackupAcknowledgementAuthorizationRejectedDetails
          details) {
    rows.add(List.of("Book file", CliTextDisplay.serializedAbsolutePath(details.bookFile())));
    rows.add(List.of("Backup file", CliTextDisplay.serializedAbsolutePath(details.backupFile())));
    rows.add(
        List.of("Backup key file", CliTextDisplay.serializedAbsolutePath(details.backupKeyFile())));
    rows.add(List.of("Backup ID", details.backupId()));
    rows.add(
        List.of("Pair publication completion", details.pairPublicationCompletion().wireValue()));
    CliProtectedBookPairPublicationPresentation.appendTextRows(rows, details.pairPublication());
  }

  private static void appendAttestationReviewRequiredRows(
      List<List<String>> rows,
      CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails details) {
    rows.add(List.of("Book ID", details.bookId()));
    CliAttestationHeadPresentation.appendIdentityRows(
        rows,
        details.verifiedAttestationHead().operationOrder(),
        details.verifiedAttestationHead().operationHead());
    rows.add(List.of(CliAttestationHeadPresentation.PREVIOUS_HEAD_LABEL, details.previousHead()));
    rows.add(
        List.of(
            "Review findings",
            CliAttestationReviewTextRenderer.renderPayloadFindings(details.reviewFindings())));
  }

  private static void appendAccountRows(
      List<List<String>> rows,
      CliAccountRejectionJsonModels.AccountRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliAccountRejectionJsonModels.AccountTypeConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Existing account type", details.existingAccountType()));
        rows.add(List.of("Requested account type", details.requestedAccountType()));
      }
      case CliAccountRejectionJsonModels.AccountTaxonomyConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        appendTaxonomyRows(rows, "Existing", details.existingAccountTaxonomy());
        appendTaxonomyRows(rows, "Requested", details.requestedAccountTaxonomy());
      }
      case CliAccountRejectionJsonModels.ContraAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Contra account code", details.contraOfAccountCode()));
        rows.add(List.of("Contra relationship", details.violation()));
      }
      case CliAccountRejectionJsonModels.AccountCodeDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliAccountRejectionJsonModels.AccountDependenciesDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Durable dependencies", CliTextFormat.joined(details.dependencies())));
      }
      case CliAccountRejectionJsonModels.ParentAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
      }
      case CliAccountRejectionJsonModels.ParentAccountTypeConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Requested account type", details.requestedAccountType()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        rows.add(List.of("Parent account type", details.parentAccountType()));
      }
      case CliAccountRejectionJsonModels.ParentAccountNodeKindDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        rows.add(List.of("Parent account node kind", details.parentAccountNodeKind()));
      }
      case CliAccountRejectionJsonModels.ParentAccountTaxonomyConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        appendTaxonomyRows(rows, "Requested", details.requestedAccountTaxonomy());
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        appendTaxonomyRows(rows, "Parent", details.parentAccountTaxonomy());
      }
      case CliAccountRejectionJsonModels.ReservedResultClassificationDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(
            List.of(
                "Financial position classification",
                details.financialPositionLineClassification()));
      }
      case CliAccountRejectionJsonModels.CloseTargetAccountCandidateMissingDetails details -> {
        rows.add(
            List.of(
                "Required financial position classification",
                details.requiredFinancialPositionLineClassification()));
        rows.add(
            List.of(
                "Inactive candidate account codes",
                CliTextFormat.joined(details.inactiveCandidateAccountCodes())));
      }
      case CliAccountRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails details -> {
        rows.add(
            List.of(
                "Required financial position classification",
                details.requiredFinancialPositionLineClassification()));
        rows.add(
            List.of(
                "Candidate account codes", CliTextFormat.joined(details.candidateAccountCodes())));
      }
    }
  }

  private static void appendCloseWindowRows(
      List<List<String>> rows,
      CliCloseRejectionJsonModels.CloseWindowRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliCloseRejectionJsonModels.InterimResultSweepStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliCloseRejectionJsonModels.InterimResultSweepFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
      case CliCloseRejectionJsonModels.InterimResultSweepFiscalYearDetails details -> {
        rows.add(List.of("Attempted start date", details.attemptedEffectiveDateFrom()));
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Fiscal year start", details.fiscalYearStart()));
      }
      case CliCloseRejectionJsonModels.FiscalYearCloseStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliCloseRejectionJsonModels.FiscalYearCloseEndDetails details ->
          rows.add(List.of("Required end date", details.requiredEffectiveDateTo()));
      case CliCloseRejectionJsonModels.FiscalYearCloseTransferredThroughDetails details -> {
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Transferred-through date", details.transferredThroughEffectiveDate()));
      }
      case CliCloseRejectionJsonModels.FiscalYearCloseFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
    }
  }

  private static void appendQueryOrPlanRows(
      List<List<String>> rows,
      CliQueryPlanRejectionJsonModels.QueryOrPlanRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliQueryPlanRejectionJsonModels.UnknownAccountDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliQueryPlanRejectionJsonModels.PostingNotFoundDetails details ->
          rows.add(List.of("Posting id", details.postingId()));
      case CliQueryPlanRejectionJsonModels.PlanRejectionDetails details ->
          rows.add(List.of("Plan id", details.plan().planId()));
    }
  }

  private static void appendTaxRows(
      List<List<String>> rows, CliTaxRejectionJsonModels.TaxRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails details -> {
        int index = 1;
        for (CliTaxRejectionJsonModels.TaxDefinitionViolationDetails violation :
            details.violations()) {
          String fieldPrefix = violation.field() == null ? violation.code() : violation.field();
          rows.add(
              List.of(
                  "Violation " + index,
                  fieldPrefix + " [" + violation.code() + "]: " + violation.message()));
          index++;
        }
      }
      case CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails details ->
          rows.add(List.of("Tax registration id", details.taxRegistrationId()));
      case CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails details -> {
        rows.add(
            List.of(
                "Obligation frequency", CliTextDisplay.wireLabel(details.obligationFrequency())));
        rows.add(List.of("Requested period start", details.effectiveDateFrom()));
        rows.add(List.of("Requested period end", details.effectiveDateTo()));
      }
    }
  }

  private static void appendTaxonomyRows(
      List<List<String>> rows,
      String labelPrefix,
      CliAccountRejectionJsonModels.AccountTaxonomyDetails details) {
    rows.add(List.of(labelPrefix + " account node kind", details.accountNodeKind()));
    rows.add(
        List.of(
            labelPrefix + " parent account",
            details.parentAccountCode() == null ? "(none)" : details.parentAccountCode()));
    rows.add(
        List.of(
            labelPrefix + " financial position classification",
            details.financialPositionLineClassification() == null
                ? "(none)"
                : details.financialPositionLineClassification()));
    rows.add(
        List.of(
            labelPrefix + " profit-and-loss classification",
            details.profitAndLossLineClassification() == null
                ? "(none)"
                : details.profitAndLossLineClassification()));
  }
}
