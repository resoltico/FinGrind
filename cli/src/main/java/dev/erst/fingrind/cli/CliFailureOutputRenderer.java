package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared human-readable rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureHuman(CliFailure failure) {
    return renderHumanDocument(
        "Error",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderDeterministicFailureHuman(CliFailure failure) {
    return renderHumanDocument(
        "Rejected",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderWarningHuman(CliFailure failure) {
    return renderHumanDocument(
        "Warning",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderInfoHuman(CliFailure failure) {
    return renderHumanDocument(
        "Info",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderRejectedHuman(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    return renderHumanDocument(
        "Rejected", code, message, hint, null, idempotencyKey, null, details);
  }

  private static String renderHumanDocument(
      String title,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      CliErrorJsonModels.@Nullable ErrorDetails details,
      CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Code", code));
    rows.add(List.of("Message", message));
    if (idempotencyKey != null) {
      rows.add(List.of("Idempotency key", idempotencyKey));
    }
    if (argument != null) {
      rows.add(List.of("Argument", argument));
    }
    if (hint != null) {
      rows.add(List.of("Hint", hint));
    }
    if (details != null) {
      switch (details) {
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
    appendRejectionDetails(rows, rejectionDetails);
    return CliTextFormat.renderTitledBlock(title, CliTextFormat.renderKeyValueBlock(rows));
  }

  private static void appendRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails) {
    if (rejectionDetails == null) {
      return;
    }
    switch (rejectionDetails) {
      case CliRejectionJsonModels.AccountStateViolationsDetails violations ->
          rows.add(
              List.of(
                  "Violations",
                  violations.violations().stream()
                      .map(violation -> violation.code() + " (" + violation.accountCode() + ")")
                      .collect(java.util.stream.Collectors.joining(", "))));
      case CliRejectionJsonModels.PriorPostingDetails details ->
          rows.add(List.of("Prior posting id", details.priorPostingId()));
      case CliRejectionJsonModels.AccountRoleConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Existing account role", details.existingAccountRole()));
        rows.add(List.of("Requested account role", details.requestedAccountRole()));
      }
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
      case CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        appendTaxonomyRows(rows, "Requested", details.requestedAccountTaxonomy());
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        appendTaxonomyRows(rows, "Parent", details.parentAccountTaxonomy());
      }
      case CliRejectionJsonModels.PostingKindDetails details ->
          rows.add(List.of("Posting kind", details.postingKind()));
      case CliRejectionJsonModels.FunctionalCurrencyMismatchDetails details -> {
        rows.add(List.of("Functional currency", details.functionalCurrency()));
        rows.add(List.of("Attempted currency", details.attemptedCurrency()));
      }
      case CliRejectionJsonModels.OpeningBalanceWindowClosedDetails details -> {
        rows.add(List.of("First blocking posting kind", details.firstBlockingPostingKind()));
        rows.add(List.of("First blocking effective date", details.firstBlockingEffectiveDate()));
      }
      case CliRejectionJsonModels.OpeningBalanceNominalAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Account type", details.accountType()));
      }
      case CliRejectionJsonModels.ClosingEquityAccountDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(
            List.of(
                "Required financial position classification",
                details.requiredFinancialPositionLineClassification()));
        rows.add(
            List.of(
                "Actual financial position classification",
                details.actualFinancialPositionLineClassification()));
      }
      case CliRejectionJsonModels.PeriodCloseStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliRejectionJsonModels.PeriodCloseFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
      case CliRejectionJsonModels.PeriodCloseFiscalYearDetails details -> {
        rows.add(List.of("Attempted start date", details.attemptedEffectiveDateFrom()));
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Fiscal year start", details.fiscalYearStart()));
      }
      case CliRejectionJsonModels.ClosedPeriodViolationDetails details -> {
        rows.add(List.of("Closed through", details.closedThroughEffectiveDate()));
        rows.add(List.of("Attempted effective date", details.attemptedEffectiveDate()));
      }
      case CliRejectionJsonModels.UnknownAccountDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliRejectionJsonModels.PostingNotFoundDetails details ->
          rows.add(List.of("Posting id", details.postingId()));
      case CliRejectionJsonModels.PlanRejectionDetails details ->
          rows.add(List.of("Plan id", details.plan().planId()));
    }
  }

  private static void appendTaxonomyRows(
      List<List<String>> rows,
      String labelPrefix,
      CliRejectionJsonModels.AccountTaxonomyDetails details) {
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
