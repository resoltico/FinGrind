package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared plain-language rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureText(CliFailure failure) {
    return renderTextDocument(
        "Error",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderDeterministicFailureText(CliFailure failure) {
    return renderTextDocument(
        "Rejected",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderWarningText(CliFailure failure) {
    return renderTextDocument(
        "Warning",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderInfoText(CliFailure failure) {
    return renderTextDocument(
        "Info",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details(),
        null);
  }

  static String renderRejectedText(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    return renderTextDocument("Rejected", code, message, hint, null, idempotencyKey, null, details);
  }

  private static String renderTextDocument(
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
    if (rejectionDetails instanceof CliRejectionJsonModels.PostingRejectionDetails postingDetails) {
      appendPostingRejectionDetails(rows, postingDetails);
      return;
    }
    if (rejectionDetails instanceof CliRejectionJsonModels.AccountRejectionDetails accountDetails) {
      appendAccountRejectionDetails(rows, accountDetails);
      return;
    }
    if (rejectionDetails
        instanceof CliRejectionJsonModels.PeriodResultTransferRejectionDetails periodDetails) {
      appendPeriodResultTransferRejectionDetails(rows, periodDetails);
      return;
    }
    if (rejectionDetails
        instanceof CliRejectionJsonModels.QueryOrPlanRejectionDetails queryOrPlanDetails) {
      appendQueryOrPlanRejectionDetails(rows, queryOrPlanDetails);
      return;
    }
    appendMaintenanceRejectionDetails(
        rows, (CliRejectionJsonModels.MaintenanceRejectionDetails) rejectionDetails);
  }

  private static void appendPostingRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.PostingRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.AccountStateViolationsDetails violations ->
          rows.add(
              List.of(
                  "Violations",
                  violations.violations().stream()
                      .map(
                          violation ->
                              violation.code()
                                  + " ("
                                  + violation.accountCode()
                                  + (violation.accountNodeKind() == null
                                      ? ""
                                      : ", " + violation.accountNodeKind())
                                  + ")")
                      .collect(java.util.stream.Collectors.joining(", "))));
      case CliRejectionJsonModels.EntrySemanticsViolationsDetails violations ->
          rows.add(
              List.of(
                  "Violations",
                  violations.violations().stream()
                      .map(
                          violation ->
                              violation.code()
                                  + " ("
                                  + (violation.field() == null
                                      ? violation.message()
                                      : violation.field() + ": " + violation.message())
                                  + ")")
                      .collect(java.util.stream.Collectors.joining(", "))));
      case CliRejectionJsonModels.PriorPostingDetails details ->
          rows.add(List.of("Prior posting id", details.priorPostingId()));
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
      case CliRejectionJsonModels.TransferredPeriodResultViolationDetails details -> {
        rows.add(List.of("Transferred through", details.transferredThroughEffectiveDate()));
        rows.add(List.of("Attempted effective date", details.attemptedEffectiveDate()));
      }
    }
  }

  private static void appendAccountRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.AccountRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
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
      case CliRejectionJsonModels.ParentAccountRoleConflictDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Requested account role", details.requestedAccountRole()));
        rows.add(List.of("Parent account code", details.parentAccountCode()));
        rows.add(List.of("Parent account role", details.parentAccountRole()));
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
      case CliRejectionJsonModels.ResultHoldingAccountDetails details ->
          rows.add(List.of("Account code", details.accountCode()));
      case CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails details -> {
        rows.add(
            List.of(
                "Required financial position classification",
                details.requiredFinancialPositionLineClassification()));
        rows.add(
            List.of(
                "Inactive candidate account codes",
                CliTextFormat.joined(details.inactiveCandidateAccountCodes())));
      }
      case CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails details -> {
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

  private static void appendPeriodResultTransferRejectionDetails(
      List<List<String>> rows,
      CliRejectionJsonModels.PeriodResultTransferRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.PeriodResultTransferStartDetails details ->
          rows.add(List.of("Required start date", details.requiredEffectiveDateFrom()));
      case CliRejectionJsonModels.PeriodResultTransferFutureDateDetails details ->
          rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
      case CliRejectionJsonModels.PeriodResultTransferFiscalYearDetails details -> {
        rows.add(List.of("Attempted start date", details.attemptedEffectiveDateFrom()));
        rows.add(List.of("Attempted end date", details.attemptedEffectiveDateTo()));
        rows.add(List.of("Fiscal year start", details.fiscalYearStart()));
      }
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

  private static void appendMaintenanceRejectionDetails(
      List<List<String>> rows,
      CliRejectionJsonModels.MaintenanceRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.BookFileDetails details ->
          rows.add(List.of("Book file", details.bookFile()));
      case CliRejectionJsonModels.BookAndBackupFileDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Backup file", details.backupFile()));
      }
      case CliRejectionJsonModels.BlockingArtifactsDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Blocking artifacts", CliTextFormat.joined(details.blockingArtifacts())));
      }
      case CliRejectionJsonModels.ArtifactBusyDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", details.artifactPath()));
      }
      case CliRejectionJsonModels.BackupFileDetails details ->
          rows.add(List.of("Backup file", details.backupFile()));
      case CliRejectionJsonModels.BackupBookKeyFileDetails details ->
          rows.add(List.of("Backup key file", details.backupBookKeyFile()));
      case CliRejectionJsonModels.ArtifactVerificationFailureDetails details -> {
        rows.add(List.of("Artifact role", details.artifactRole()));
        rows.add(List.of("Artifact path", details.artifactPath()));
        rows.add(List.of("Verification failure", details.verificationFailure()));
      }
      case CliRejectionJsonModels.RollbackArtifactDetails details ->
          rows.add(List.of("Rollback artifact", details.rollbackArtifact()));
      case CliRejectionJsonModels.RollbackArtifactMismatchDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Rollback artifact", details.rollbackArtifact()));
      }
      case CliRejectionJsonModels.RollbackArtifactSelectionDetails details -> {
        rows.add(List.of("Book file", details.bookFile()));
        rows.add(List.of("Rollback artifacts", CliTextFormat.joined(details.rollbackArtifacts())));
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
