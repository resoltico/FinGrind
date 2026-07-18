package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared plain-language rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureText(CliFailure failure) {
    return renderTextDocument(TextDocument.failure("Error", failure));
  }

  static String renderDeterministicFailureText(CliFailure failure) {
    return renderTextDocument(TextDocument.failure("Rejected", failure));
  }

  static String renderRejectedText(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    @Nullable String dedicatedPostingText =
        CliPostingRejectionTextRenderer.renderDedicatedRejectedText(
            code, message, idempotencyKey, details);
    if (dedicatedPostingText != null) {
      return dedicatedPostingText;
    }
    return renderTextDocument(TextDocument.rejection(code, message, hint, idempotencyKey, details));
  }

  private static String renderTextDocument(TextDocument document) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Code", document.code()));
    rows.add(List.of("Message", document.message()));
    if (document.idempotencyKey() != null) {
      rows.add(List.of("Idempotency key", document.idempotencyKey()));
    }
    if (document.argument() != null) {
      rows.add(List.of("Argument", document.argument()));
    }
    if (document.hint() != null) {
      rows.add(List.of("Hint", document.hint()));
    }
    if (document.details() != null) {
      switch (document.details()) {
        case CliErrorJsonModels.InvalidJsonDetails invalidJsonDetails -> {
          rows.add(List.of("Parse message", invalidJsonDetails.parseMessage()));
          rows.add(
              List.of(
                  "Parse location",
                  "line " + invalidJsonDetails.line() + ", column " + invalidJsonDetails.column()));
        }
        case CliErrorJsonModels.InvalidRequestDetails invalidRequestDetails ->
            rows.add(
                List.of("Violations", CliTextFormat.joined(invalidRequestDetails.violations())));
      }
    }
    if (document.pathFailure() != null) {
      appendFailurePaths(rows, document.pathFailure());
    }
    appendRejectionDetails(rows, document.rejectionDetails());
    return CliTextFormat.renderTitledBlock(
        document.title(), CliTextFormat.renderKeyValueBlock(rows));
  }

  private record TextDocument(
      String title,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      CliErrorJsonModels.@Nullable ErrorDetails details,
      CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails,
      @Nullable CliFailure pathFailure) {
    static TextDocument failure(String title, CliFailure failure) {
      return new TextDocument(
          title,
          failure.code(),
          failure.message(),
          failure.hint(),
          failure.argument(),
          null,
          failure.details(),
          null,
          failure);
    }

    static TextDocument rejection(
        String code,
        String message,
        @Nullable String hint,
        @Nullable String idempotencyKey,
        CliRejectionJsonModels.@Nullable RejectionDetails details) {
      return new TextDocument(
          "Rejected", code, message, hint, null, idempotencyKey, null, details, null);
    }
  }

  private static void appendFailurePaths(List<List<String>> rows, CliFailure failure) {
    if (failure.path() == null) {
      return;
    }
    rows.add(List.of("Path", CliTextDisplay.path(failure.path())));
    if (!failure.relatedPaths().isEmpty()) {
      rows.add(
          List.of(
              "Related paths",
              CliTextFormat.joined(
                  failure.relatedPaths().stream().map(CliTextDisplay::path).toList())));
    }
  }

  private static void appendRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails) {
    if (rejectionDetails == null) {
      return;
    }
    if (rejectionDetails instanceof CliRejectionJsonModels.PostingRejectionDetails postingDetails) {
      CliPostingRejectionTextRenderer.appendRows(rows, postingDetails);
      return;
    }
    if (rejectionDetails instanceof CliRejectionJsonModels.AccountRejectionDetails accountDetails) {
      appendAccountRejectionDetails(rows, accountDetails);
      return;
    }
    if (rejectionDetails
        instanceof CliRejectionJsonModels.CloseWindowRejectionDetails closeWindowDetails) {
      appendCloseWindowRejectionDetails(rows, closeWindowDetails);
      return;
    }
    if (rejectionDetails
        instanceof CliRejectionJsonModels.QueryOrPlanRejectionDetails queryOrPlanDetails) {
      appendQueryOrPlanRejectionDetails(rows, queryOrPlanDetails);
      return;
    }
    if (rejectionDetails instanceof CliTaxRejectionJsonModels.TaxRejectionDetails taxDetails) {
      appendTaxRejectionDetails(rows, taxDetails);
      return;
    }
    CliMaintenanceFailureOutputRenderer.appendRows(
        rows, (CliRejectionJsonModels.MaintenanceRejectionDetails) rejectionDetails);
  }

  private static void appendAccountRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.AccountRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.AccountTypeConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Existing account type", details.existingAccountType()));
        rows.add(List.of("Requested account type", details.requestedAccountType()));
      }
      case CliRejectionJsonModels.AccountTaxonomyConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        appendTaxonomyRows(rows, "Existing", details.existingAccountTaxonomy());
        appendTaxonomyRows(rows, "Requested", details.requestedAccountTaxonomy());
      }
      case CliRejectionJsonModels.ContraAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Contra account code", details.contraOfAccountCode()));
        rows.add(List.of("Contra relationship", details.violation()));
      }
      case CliRejectionJsonModels.AccountCodeDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliRejectionJsonModels.AccountDependenciesDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Durable dependencies", CliTextFormat.joined(details.dependencies())));
      }
      case CliRejectionJsonModels.ParentAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
      }
      case CliRejectionJsonModels.ParentAccountTypeConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Requested account type", details.requestedAccountType()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        rows.add(List.of("Parent account type", details.parentAccountType()));
      }
      case CliRejectionJsonModels.ParentAccountNodeKindDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        rows.add(List.of("Parent account node kind", details.parentAccountNodeKind()));
      }
      case CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        appendTaxonomyRows(rows, "Requested", details.requestedAccountTaxonomy());
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        appendTaxonomyRows(rows, "Parent", details.parentAccountTaxonomy());
      }
      case CliRejectionJsonModels.ReservedResultClassificationDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(
            List.of(
                "Financial position classification",
                details.financialPositionLineClassification()));
      }
      case CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails details -> {
        rows.add(
            List.of(
                "Required financial position classification",
                details.requiredFinancialPositionLineClassification()));
        rows.add(
            List.of(
                "Inactive candidate account codes",
                CliTextFormat.joined(details.inactiveCandidateAccountCodes())));
      }
      case CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails details -> {
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

  private static void appendCloseWindowRejectionDetails(
      List<List<String>> rows,
      CliRejectionJsonModels.CloseWindowRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.InterimResultSweepStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliRejectionJsonModels.InterimResultSweepFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
      case CliRejectionJsonModels.InterimResultSweepFiscalYearDetails details -> {
        rows.add(List.of("Attempted start date", details.attemptedEffectiveDateFrom()));
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Fiscal year start", details.fiscalYearStart()));
      }
      case CliRejectionJsonModels.FiscalYearCloseStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliRejectionJsonModels.FiscalYearCloseEndDetails details ->
          rows.add(List.of("Required end date", details.requiredEffectiveDateTo()));
      case CliRejectionJsonModels.FiscalYearCloseTransferredThroughDetails details -> {
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Transferred-through date", details.transferredThroughEffectiveDate()));
      }
      case CliRejectionJsonModels.FiscalYearCloseFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
    }
  }

  private static void appendQueryOrPlanRejectionDetails(
      List<List<String>> rows,
      CliRejectionJsonModels.QueryOrPlanRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.UnknownAccountDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliRejectionJsonModels.PostingNotFoundDetails details ->
          rows.add(List.of("Posting id", details.postingId()));
      case CliRejectionJsonModels.PlanRejectionDetails details ->
          rows.add(List.of("Plan id", details.plan().planId()));
    }
  }

  private static void appendTaxRejectionDetails(
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
      CliRejectionJsonModels.AccountTaxonomyDetails details) {
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
