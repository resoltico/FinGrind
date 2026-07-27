package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
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

  static String renderAccountDeclarationText(
      CliDeclareAccountPayload.Outcome outcome,
      DeclaredAccount account,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Outcome", outcome.wireValue()));
    rows.add(List.of("Account code", account.accountCode().value()));
    rows.add(List.of("Account name", account.accountName().value()));
    rows.add(
        List.of(
            "Parent account",
            account
                .accountTaxonomy()
                .parentAccountCode()
                .map(parent -> parent.value())
                .orElse("(none)")));
    rows.add(
        List.of(
            "Account type", CliAccountStatementLabels.displayLineTypeLabel(account.accountType())));
    rows.add(
        List.of(
            "Financial-position line",
            account
                .accountTaxonomy()
                .financialPositionLineClassification()
                .map(CliAccountStatementLabels::displayFinancialPositionLineClassification)
                .orElse("(none)")));
    rows.add(
        List.of(
            "Profit-and-loss line",
            account
                .accountTaxonomy()
                .profitAndLossLineClassification()
                .map(CliAccountStatementLabels::displayProfitAndLossLineClassification)
                .orElse("(none)")));
    rows.add(List.of("Unit of measure", displayUnitOfMeasure(account)));
    rows.add(
        List.of(
            "Normal balance",
            CliAccountStatementLabels.displayNormalBalanceLabel(account.normalBalance())));
    rows.add(List.of("Active", CliQueryScopeText.displayBooleanLabel(account.active())));
    rows.add(List.of("Declared at", CliTextDisplay.instant(account.declaredAt())));
    CliAttestationCommitPresentation.appendTextRows(
        rows, attestationCommit, "No operation appended (unchanged account definition)");
    return CliTextFormat.renderTitledBlock(
        outcome.textTitle(), CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
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
    CliAttestationCommitPresentation.appendTextRows(
        rows,
        committed.attestationCommit(),
        committed.idempotentReplay()
            ? "No operation appended (idempotent replay)"
            : "No attestation operation was returned by the persistence adapter");
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

  private static String displayUnitOfMeasure(DeclaredAccount account) {
    return account.unitOfMeasure() == null
        ? "(none)"
        : account.unitOfMeasure().token()
            + " (scale "
            + account.unitOfMeasure().quantityScale()
            + ")";
  }
}
