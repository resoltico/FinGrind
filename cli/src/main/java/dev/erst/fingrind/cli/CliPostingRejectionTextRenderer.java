package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountStateViolationPayload;
import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Dedicated plain-language rendering for posting-side deterministic rejections. */
final class CliPostingRejectionTextRenderer {
  private CliPostingRejectionTextRenderer() {}

  static @Nullable String renderDedicatedRejectedText(
      String code,
      String summary,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    if (!(details instanceof CliRejectionJsonModels.PostingRejectionDetails postingDetails)) {
      return null;
    }
    return switch (postingDetails) {
      case CliRejectionJsonModels.AccountStateViolationsDetails violations ->
          renderNestedRepairablePostingRejectionText(
              code,
              summary,
              idempotencyKey,
              renderAccountStateIssueSections(violations.violations()));
      case CliRejectionJsonModels.EntrySemanticsViolationsDetails violations ->
          renderNestedRepairablePostingRejectionText(
              code,
              summary,
              idempotencyKey,
              renderEntrySemanticsIssueSections(violations.violations()));
      default -> null;
    };
  }

  static void appendRows(
      List<List<String>> rows, CliRejectionJsonModels.PostingRejectionDetails rejectionDetails) {
    switch (rejectionDetails) {
      case CliRejectionJsonModels.AccountStateViolationsDetails _ ->
          throw new IllegalStateException(
              "Nested repairable posting rejections must use the dedicated text renderer.");
      case CliRejectionJsonModels.EntrySemanticsViolationsDetails _ ->
          throw new IllegalStateException(
              "Nested repairable posting rejections must use the dedicated text renderer.");
      case CliRejectionJsonModels.PriorPostingDetails details ->
          rows.add(List.of("Prior posting id", details.priorPostingId()));
      case CliRejectionJsonModels.FunctionalCurrencyMismatchDetails details -> {
        rows.add(List.of("Functional currency", details.functionalCurrency()));
        rows.add(List.of("Attempted currency", details.attemptedCurrency()));
      }
      case CliRejectionJsonModels.OpeningPositionWindowClosedDetails details -> {
        rows.add(List.of("First blocking posting kind", details.firstBlockingPostingKind()));
        rows.add(List.of("First blocking effective date", details.firstBlockingEffectiveDate()));
      }
      case CliRejectionJsonModels.OpeningPositionNominalAccountDetails details -> {
        rows.add(List.of("Account code", details.accountCode()));
        rows.add(List.of("Account type", details.accountType()));
      }
      case CliRejectionJsonModels.SweptInterimResultViolationDetails details -> {
        rows.add(List.of("Transferred through", details.transferredThroughEffectiveDate()));
        rows.add(List.of("Attempted effective date", details.attemptedEffectiveDate()));
      }
    }
  }

  private static String renderNestedRepairablePostingRejectionText(
      String code, String summary, @Nullable String idempotencyKey, List<String> detailSections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Code", code));
    rows.add(List.of("Summary", summary));
    if (idempotencyKey != null) {
      rows.add(List.of("Idempotency key", idempotencyKey));
    }
    String body = CliTextFormat.renderKeyValueBlock(rows);
    body =
        body
            + System.lineSeparator()
            + System.lineSeparator()
            + String.join(System.lineSeparator() + System.lineSeparator(), detailSections);
    return CliTextFormat.renderTitledBlock("Rejected", body);
  }

  private static String renderIssueSection(int issueNumber, String code, List<List<String>> rows) {
    return CliTextFormat.renderSummaryBlock(
        "Issue %d | %s".formatted(issueNumber, code), CliTextFormat.renderKeyValueBlock(rows));
  }

  private static List<String> renderAccountStateIssueSections(
      List<CliAccountStateViolationPayload> violations) {
    List<String> rendered = new ArrayList<>(violations.size());
    for (int index = 0; index < violations.size(); index++) {
      rendered.add(renderAccountStateIssueSection(index + 1, violations.get(index)));
    }
    return rendered;
  }

  private static String renderAccountStateIssueSection(
      int issueNumber, CliAccountStateViolationPayload violation) {
    List<List<String>> issueRows = new ArrayList<>();
    issueRows.add(List.of("Field", violation.field()));
    issueRows.add(List.of("Category", violation.category()));
    issueRows.add(List.of("Account code", violation.accountCode()));
    if (violation.accountNodeKind() != null) {
      issueRows.add(List.of("Account node kind", violation.accountNodeKind()));
    }
    issueRows.add(List.of("Why", violation.message()));
    issueRows.add(List.of("Repair", violation.repair()));
    return renderIssueSection(issueNumber, violation.code(), issueRows);
  }

  private static List<String> renderEntrySemanticsIssueSections(
      List<CliEntrySemanticsViolationPayload> violations) {
    List<String> rendered = new ArrayList<>(violations.size());
    for (int index = 0; index < violations.size(); index++) {
      rendered.add(renderEntrySemanticsIssueSection(index + 1, violations.get(index)));
    }
    return rendered;
  }

  private static String renderEntrySemanticsIssueSection(
      int issueNumber, CliEntrySemanticsViolationPayload violation) {
    List<List<String>> issueRows = new ArrayList<>();
    if (violation.field() != null) {
      issueRows.add(List.of("Field", violation.field()));
    }
    issueRows.add(List.of("Category", violation.category()));
    issueRows.add(List.of("Why", violation.message()));
    issueRows.add(List.of("Repair", violation.repair()));
    return renderIssueSection(issueNumber, violation.code(), issueRows);
  }
}
