package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Comparator;
import java.util.List;

/** Shared plain-language rendering for account declaration and posting mutations. */
final class CliMutationOutputRenderer {
  private CliMutationOutputRenderer() {}

  static String renderAccountDeclarationText(String outcome, DeclaredAccount account) {
    return CliTextFormat.renderTitledBlock(
        accountDeclarationTitle(outcome),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Outcome", outcome),
                List.of("Account code", account.accountCode().value()),
                List.of("Account name", account.accountName().value()),
                List.of(
                    "Parent account",
                    account
                        .accountTaxonomy()
                        .parentAccountCode()
                        .map(parent -> parent.value())
                        .orElse("(none)")),
                List.of(
                    "Account type",
                    CliAccountStatementLabels.displayLineTypeLabel(account.accountType())),
                List.of(
                    "Financial-position line",
                    account
                        .accountTaxonomy()
                        .financialPositionLineClassification()
                        .map(CliAccountStatementLabels::displayFinancialPositionLineClassification)
                        .orElse("(none)")),
                List.of(
                    "Profit-and-loss line",
                    account
                        .accountTaxonomy()
                        .profitAndLossLineClassification()
                        .map(CliAccountStatementLabels::displayProfitAndLossLineClassification)
                        .orElse("(none)")),
                List.of(
                    "Normal balance",
                    CliAccountStatementLabels.displayNormalBalanceLabel(account.normalBalance())),
                List.of("Active", CliQueryScopeText.displayBooleanLabel(account.active())),
                List.of("Declared at", CliTextDisplay.instant(account.declaredAt())))));
  }

  static String renderPreflightAcceptedText(PostEntryResult.PreflightAccepted accepted) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Idempotency key", accepted.idempotencyKey().value()));
    rows.add(List.of("Effective date", accepted.effectiveDate().toString()));
    rows.add(List.of("Commit status", "Not committed"));
    appendResolvedJournalRows(rows, accepted.resolvedJournal());
    return CliTextFormat.renderTitledBlock(
        "Entry Preflight Passed",
        CliReportRenderSupport.joinSections(
            CliTextFormat.renderKeyValueBlock(List.copyOf(rows)),
            CliReportRenderSupport.section(
                "Journal lines",
                CliJournalLineTextRenderer.renderLines(
                    accepted.resolvedJournal().expandedLines().lines()))));
  }

  static String renderCommittedText(PostEntryResult.Committed committed) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Posting id", committed.postingId().value()));
    rows.add(List.of("Idempotency key", committed.idempotencyKey().value()));
    rows.add(List.of("Effective date", committed.effectiveDate().toString()));
    rows.add(List.of("Recorded at", CliTextDisplay.instant(committed.recordedAt())));
    rows.add(
        List.of(
            "Idempotent replay",
            CliQueryScopeText.displayBooleanLabel(committed.idempotentReplay())));
    appendResolvedJournalRows(rows, committed.resolvedJournal());
    return CliTextFormat.renderTitledBlock(
        "Entry Committed",
        CliReportRenderSupport.joinSections(
            CliTextFormat.renderKeyValueBlock(List.copyOf(rows)),
            CliReportRenderSupport.section(
                "Journal lines",
                CliJournalLineTextRenderer.renderLines(
                    committed.resolvedJournal().expandedLines().lines()))));
  }

  private static void appendResolvedJournalRows(
      List<List<String>> rows, ResolvedJournal resolvedJournal) {
    ClassificationResult classification = resolvedJournal.classification();
    rows.add(List.of("Event class", classification.eventClass().wireValue()));
    if (shouldRenderContainedTypedEvents(classification)) {
      rows.add(List.of("Contained typed events", containedTypedEventsLabel(classification)));
    }
  }

  private static boolean shouldRenderContainedTypedEvents(ClassificationResult classification) {
    if (classification.containedTypedEvents().isEmpty()) {
      return true;
    }
    return classification.containedTypedEvents().size() != 1
        || !classification.containedTypedEvents().contains(classification.eventClass());
  }

  private static String containedTypedEventsLabel(ClassificationResult classification) {
    if (classification.containedTypedEvents().isEmpty()) {
      return "(none)";
    }
    return classification.containedTypedEvents().stream()
        .sorted(Comparator.comparing(EconomicEventClass::wireValue))
        .map(EconomicEventClass::wireValue)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String accountDeclarationTitle(String outcome) {
    return switch (outcome) {
      case "declared" -> "Account Declared";
      case "reactivated" -> "Account Reactivated";
      case "renamed" -> "Account Renamed";
      case "unchanged" -> "Account Unchanged";
      default -> "Account Updated";
    };
  }
}
