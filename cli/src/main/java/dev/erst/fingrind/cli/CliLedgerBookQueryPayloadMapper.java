package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps ledger-fact groups into book-query JSON payloads. */
final class CliLedgerBookQueryPayloadMapper {
  private CliLedgerBookQueryPayloadMapper() {}

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountName"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountType"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountNodeKind"),
        CliLedgerFactAccess.optionalTextFact(facts, "parentAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "financialPositionLineClassification"),
        CliLedgerFactAccess.optionalTextFact(facts, "cashFlowAssetClassification"),
        CliLedgerFactAccess.optionalTextFact(facts, "profitAndLossLineClassification"),
        CliLedgerFactAccess.requiredTextFact(facts, "normalBalance"),
        CliLedgerFactAccess.requiredFlagFact(facts, "active"),
        CliLedgerFactAccess.requiredTextFact(facts, "declaredAt"));
  }

  static CliBookQueryJsonModels.PostingPayload postingPayload(List<LedgerFact> facts) {
    List<LedgerFact> provenanceFacts = CliLedgerFactAccess.requiredGroupFacts(facts, "provenance");
    List<LedgerFact> evidenceFacts = CliLedgerFactAccess.requiredGroupFacts(facts, "evidence");
    @Nullable List<LedgerFact> entryFacts = CliLedgerFactAccess.optionalGroupFacts(facts, "entry");
    @Nullable List<LedgerFact> reversalFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "reversal");
    return new CliBookQueryJsonModels.PostingPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "postingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "postingKind"),
        CliLedgerFactAccess.requiredTextFact(facts, "postingOriginKind"),
        CliLedgerFactAccess.requiredTextFact(facts, "reversalState"),
        reversalFacts == null
            ? null
            : CliLedgerFactAccess.optionalTextFact(reversalFacts, "priorPostingId"),
        CliLedgerFactAccess.optionalTextFact(facts, "reversedByPostingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"),
        CliLedgerFactAccess.requiredTextFact(facts, "recordedAt"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "actorId"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "actorType"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "commandId"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "idempotencyKey"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "causationId"),
        CliLedgerFactAccess.optionalTextFact(provenanceFacts, "correlationId"),
        CliLedgerFactAccess.requiredTextFact(provenanceFacts, "sourceChannel"),
        evidencePayload(evidenceFacts),
        entryFacts == null ? null : entryPayload(entryFacts),
        reversalFacts == null ? null : reversalPayload(reversalFacts),
        CliLedgerFactAccess.groupedFacts(facts, "line").stream()
            .map(CliLedgerBookQueryPayloadMapper::linePayload)
            .toList());
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      List<LedgerFact> facts) {
    @Nullable List<LedgerFact> reversalFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "reversal");
    List<LedgerFact> evidenceFacts = CliLedgerFactAccess.requiredGroupFacts(facts, "evidence");
    return new CliBookQueryJsonModels.PostingSummaryPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "postingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "postingKind"),
        CliLedgerFactAccess.requiredTextFact(facts, "postingOriginKind"),
        CliLedgerFactAccess.requiredTextFact(facts, "reversalState"),
        reversalFacts == null
            ? null
            : CliLedgerFactAccess.optionalTextFact(reversalFacts, "priorPostingId"),
        CliLedgerFactAccess.optionalTextFact(facts, "reversedByPostingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"),
        CliLedgerFactAccess.requiredTextFact(facts, "recordedAt"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "debitTotal"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "creditTotal"),
        CliLedgerFactAccess.textFacts(facts, "accountCode"),
        CliLedgerFactAccess.groupedFacts(evidenceFacts, "sourceDocument").stream()
            .map(groupFacts -> CliLedgerFactAccess.requiredTextFact(groupFacts, "sourceDocumentId"))
            .toList(),
        CliLedgerFactAccess.groupedFacts(evidenceFacts, "approval").stream()
            .map(groupFacts -> CliLedgerFactAccess.requiredTextFact(groupFacts, "approvalId"))
            .toList());
  }

  static CliBookQueryJsonModels.AccountingEvidencePayload evidencePayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.AccountingEvidencePayload(
        CliLedgerFactAccess.groupedFacts(facts, "sourceDocument").stream()
            .map(CliLedgerBookQueryPayloadMapper::sourceDocumentPayload)
            .toList(),
        CliLedgerFactAccess.groupedFacts(facts, "approval").stream()
            .map(CliLedgerBookQueryPayloadMapper::approvalPayload)
            .toList());
  }

  static CliBookQueryJsonModels.BalanceBucketPayload balanceBucketPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.BalanceBucketPayload(
        CliLedgerFactAccess.requiredMoneyFact(facts, "debitTotal"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "creditTotal"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "netAmount"),
        CliLedgerFactAccess.requiredTextFact(facts, "balanceSide"));
  }

  private static CliBookQueryJsonModels.SourceDocumentPayload sourceDocumentPayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.SourceDocumentPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "sourceDocumentId"),
        CliLedgerFactAccess.requiredTextFact(facts, "sourceDocumentType"),
        CliLedgerFactAccess.requiredTextFact(facts, "documentDate"));
  }

  private static CliBookQueryJsonModels.ApprovalPayload approvalPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.ApprovalPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "approvalId"),
        CliLedgerFactAccess.requiredTextFact(facts, "approvalType"),
        CliLedgerFactAccess.requiredTextFact(facts, "approverId"),
        CliLedgerFactAccess.requiredTextFact(facts, "approverType"),
        CliLedgerFactAccess.requiredTextFact(facts, "decision"),
        CliLedgerFactAccess.requiredTextFact(facts, "approvedAt"));
  }

  private static CliBookQueryJsonModels.ReversalPayload reversalPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.ReversalPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "priorPostingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "reason"));
  }

  private static CliPostingEntryPayload entryPayload(List<LedgerFact> facts) {
    @Nullable List<LedgerFact> reversalFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "reversal");
    List<CliOpeningBalancePayload> openingBalances =
        CliLedgerFactAccess.groupedFacts(facts, "openingBalance").stream()
            .map(CliLedgerBookQueryPayloadMapper::openingBalancePayload)
            .toList();
    return new CliPostingEntryPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "entryKind"),
        CliLedgerFactAccess.optionalTextFact(facts, "cashAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "revenueAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "expenseAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "equityAccountCode"),
        CliLedgerFactAccess.optionalMoneyFact(facts, "amount"),
        null,
        null,
        null,
        reversalFacts == null ? null : reversalPayload(reversalFacts),
        openingBalances.isEmpty() ? null : openingBalances);
  }

  private static CliOpeningBalancePayload openingBalancePayload(List<LedgerFact> facts) {
    return new CliOpeningBalancePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "side"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "amount"));
  }

  private static CliBookQueryJsonModels.JournalLinePayload linePayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "side"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "amount"));
  }
}
