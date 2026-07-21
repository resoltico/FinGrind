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
    @Nullable List<LedgerFact> unitOfMeasureFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "unitOfMeasure");
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountName"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountType"),
        CliLedgerFactAccess.requiredTextFact(facts, "accountNodeKind"),
        CliLedgerFactAccess.optionalTextFact(facts, "parentAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "contraOfAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "financialPositionLineClassification"),
        CliLedgerFactAccess.optionalTextFact(facts, "cashFlowAssetClassification"),
        CliLedgerFactAccess.optionalTextFact(facts, "profitAndLossLineClassification"),
        unitOfMeasureFacts == null ? null : unitOfMeasurePayload(unitOfMeasureFacts),
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
        CliLedgerFactAccess.requiredTextFact(facts, "approverReference"),
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
    @Nullable List<LedgerFact> inventoryReliefFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "inventoryRelief");
    @Nullable List<LedgerFact> settlementAdjunctFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "settlementAdjunct");
    @Nullable List<LedgerFact> latvianMonthlyPayrollFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "latvianMonthlyPayroll");
    @Nullable List<LedgerFact> latvianPayrollSettlementFacts =
        CliLedgerFactAccess.optionalGroupFacts(facts, "latvianPayrollSettlement");
    List<CliOpeningBalancePayload> openingBalances =
        CliLedgerFactAccess.groupedFacts(facts, "openingBalance").stream()
            .map(CliLedgerBookQueryPayloadMapper::openingBalancePayload)
            .toList();
    return new CliPostingEntryPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "entryKind"),
        CliLedgerFactAccess.optionalTextFact(facts, "cashAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "receivableAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "payableAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "revenueAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "inventoryAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "expenseAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "writeDownLossAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "shrinkageLossAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "countGainAccountCode"),
        CliLedgerFactAccess.optionalTextFact(facts, "equityAccountCode"),
        CliLedgerFactAccess.optionalMoneyFact(facts, "amount"),
        CliLedgerFactAccess.optionalTextFact(facts, "quantity"),
        CliLedgerFactAccess.optionalMoneyFact(facts, "unitCost"),
        inventoryReliefFacts == null
            ? null
            : new CliPostingEntryPayload.InventoryReliefPayload(
                CliLedgerFactAccess.requiredTextFact(inventoryReliefFacts, "inventoryAccountCode"),
                CliLedgerFactAccess.requiredTextFact(
                    inventoryReliefFacts, "costOfSalesAccountCode"),
                CliLedgerFactAccess.requiredTextFact(inventoryReliefFacts, "quantity")),
        settlementAdjunctFacts == null
            ? null
            : new CliPostingEntryPayload.SettlementAdjunctPayload(
                CliLedgerFactAccess.requiredTextFact(settlementAdjunctFacts, "accountCode"),
                CliLedgerFactAccess.requiredMoneyFact(settlementAdjunctFacts, "amount")),
        null,
        null,
        null,
        reversalFacts == null ? null : reversalPayload(reversalFacts),
        openingBalances.isEmpty() ? null : openingBalances,
        null,
        null,
        latvianMonthlyPayrollFacts == null
            ? null
            : latvianMonthlyPayrollPayload(latvianMonthlyPayrollFacts),
        latvianPayrollSettlementFacts == null
            ? null
            : latvianPayrollSettlementPayload(latvianPayrollSettlementFacts),
        null);
  }

  private static CliPostingEntryPayload.LatvianMonthlyPayrollPayload latvianMonthlyPayrollPayload(
      List<LedgerFact> facts) {
    return new CliPostingEntryPayload.LatvianMonthlyPayrollPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "payrollRunId"),
        CliLedgerFactAccess.requiredTextFact(facts, "employeeReference"),
        CliLedgerFactAccess.requiredTextFact(facts, "payrollMonth"),
        CliLedgerFactAccess.requiredFlagFact(facts, "taxBookHeldAtEmployer"),
        CliLedgerFactAccess.requiredCountFact(facts, "dependantCount"),
        CliLedgerFactAccess.requiredTextFact(facts, "wageExpenseAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "employerSocialContributionExpenseAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "netWagesPayableAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "employeeSocialContributionPayableAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "employerSocialContributionPayableAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "personalIncomeTaxPayableAccountCode"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "grossWages"),
        new CliPostingEntryPayload.ResolvedLatvianMonthlyPayrollCalculationPayload(
            CliLedgerFactAccess.requiredMoneyFact(facts, "employeeSocialContribution"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "employerSocialContribution"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "monthlyNonTaxableMinimum"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "personalIncomeTax"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "netWages")));
  }

  private static CliPostingEntryPayload.LatvianPayrollSettlementPayload
      latvianPayrollSettlementPayload(List<LedgerFact> facts) {
    return new CliPostingEntryPayload.LatvianPayrollSettlementPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "settlementKind"),
        CliLedgerFactAccess.requiredTextFact(facts, "payrollRunId"),
        CliLedgerFactAccess.requiredTextFact(facts, "cashAccountCode"),
        new CliPostingEntryPayload.ResolvedLatvianPayrollSettlementPayload(
            CliLedgerFactAccess.requiredTextFact(facts, "netWagesPayableAccountCode"),
            CliLedgerFactAccess.requiredTextFact(
                facts, "employeeSocialContributionPayableAccountCode"),
            CliLedgerFactAccess.requiredTextFact(
                facts, "employerSocialContributionPayableAccountCode"),
            CliLedgerFactAccess.requiredTextFact(facts, "personalIncomeTaxPayableAccountCode"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "netWages"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "employeeSocialContribution"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "employerSocialContribution"),
            CliLedgerFactAccess.requiredMoneyFact(facts, "personalIncomeTax")));
  }

  private static CliOpeningBalancePayload openingBalancePayload(List<LedgerFact> facts) {
    return new CliOpeningBalancePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "side"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "amount"),
        CliLedgerFactAccess.optionalTextFact(facts, "quantity"));
  }

  private static CliBookQueryJsonModels.JournalLinePayload linePayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "accountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "side"),
        CliLedgerFactAccess.requiredMoneyFact(facts, "amount"));
  }

  private static CliBookQueryJsonModels.UnitOfMeasurePayload unitOfMeasurePayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.UnitOfMeasurePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "token"),
        CliLedgerFactAccess.requiredCountFact(facts, "quantityScale"));
  }
}
