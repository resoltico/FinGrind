package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical post-entry request field names shared by parser and machine contract surfaces. */
public final class ProtocolPostEntryFields {
  private ProtocolPostEntryFields() {}

  /** Returns top-level posting request fields in stable wire order. */
  public static List<String> topLevelFields() {
    return List.of(
        ProtocolBusinessEventFields.Core.ENTRY_KIND,
        ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
        ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID,
        ProtocolBusinessEventFields.FixedAsset.FIXED_ASSET_ID,
        ProtocolBusinessEventFields.FixedAsset.ASSET_ACCOUNT_CODE,
        ProtocolBusinessEventFields.FixedAsset.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE,
        ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_EXPENSE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.FixedAsset.DISPOSAL_GAIN_ACCOUNT_CODE,
        ProtocolBusinessEventFields.FixedAsset.DISPOSAL_LOSS_ACCOUNT_CODE,
        ProtocolBusinessEventFields.FixedAsset.COST,
        ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_SCHEDULE,
        ProtocolBusinessEventFields.FixedAsset.PROCEEDS,
        ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID,
        ProtocolBusinessEventFields.Financing.PRINCIPAL_LIABILITY_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Financing.INTEREST_PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Financing.INTEREST_EXPENSE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Financing.PRINCIPAL_AMOUNT,
        ProtocolBusinessEventFields.Financing.INTEREST_AMOUNT,
        ProtocolBusinessEventFields.RealizedForeignExchange.FOREIGN_CURRENCY_OBLIGATION_ID,
        ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_GAIN_ACCOUNT_CODE,
        ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_LOSS_ACCOUNT_CODE,
        ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE,
        ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Inventory.WRITE_DOWN_LOSS_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Inventory.SHRINKAGE_LOSS_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Inventory.COUNT_GAIN_ACCOUNT_CODE,
        ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID,
        ProtocolBusinessEventFields.LatvianPayroll.EMPLOYEE_REFERENCE,
        ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_MONTH,
        ProtocolBusinessEventFields.LatvianPayroll.WAGE_EXPENSE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll
            .EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll.NET_WAGES_PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll
            .EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll
            .EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE,
        ProtocolBusinessEventFields.LatvianPayroll.GROSS_WAGES,
        ProtocolBusinessEventFields.Core.AMOUNT,
        ProtocolBusinessEventFields.Inventory.QUANTITY,
        ProtocolBusinessEventFields.Inventory.UNIT_COST,
        ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL,
        ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
        ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT,
        ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
        ProtocolBusinessEventFields.Core.TAX,
        ProtocolBusinessEventFields.Core.LINES,
        ProtocolBusinessEventFields.Core.OPENING_BALANCES,
        ProtocolBusinessEventFields.Core.EVIDENCE,
        ProtocolBusinessEventFields.Core.PROVENANCE,
        ProtocolBusinessEventFields.Core.REVERSAL);
  }

  /** Returns journal-line request fields in stable wire order. */
  public static List<String> journalLineFields() {
    return List.of(JournalLine.ACCOUNT_CODE, JournalLine.SIDE, JournalLine.AMOUNT);
  }

  /** Returns opening-balance request fields in stable wire order. */
  public static List<String> openingBalanceFields() {
    return List.of(
        OpeningBalance.ACCOUNT_CODE,
        OpeningBalance.SIDE,
        OpeningBalance.AMOUNT,
        OpeningBalance.QUANTITY);
  }

  /** Returns provenance request fields in stable wire order. */
  public static List<String> provenanceFields() {
    return List.of(
        Provenance.ACTOR_ID,
        Provenance.ACTOR_TYPE,
        Provenance.COMMAND_ID,
        Provenance.IDEMPOTENCY_KEY,
        Provenance.CAUSATION_ID,
        Provenance.CORRELATION_ID);
  }

  /** Returns evidence request fields in stable wire order. */
  public static List<String> evidenceFields() {
    return List.of(Evidence.SOURCE_DOCUMENTS, Evidence.APPROVALS);
  }

  /** Returns source-document evidence fields in stable wire order. */
  public static List<String> sourceDocumentFields() {
    return List.of(
        SourceDocument.SOURCE_DOCUMENT_ID,
        SourceDocument.SOURCE_DOCUMENT_TYPE,
        SourceDocument.DOCUMENT_DATE);
  }

  /** Returns approval evidence fields in stable wire order. */
  public static List<String> approvalFields() {
    return List.of(
        Approval.APPROVAL_ID,
        Approval.APPROVAL_TYPE,
        Approval.APPROVER_ID,
        Approval.APPROVER_TYPE,
        Approval.DECISION,
        Approval.APPROVED_AT);
  }

  /** Returns reversal request fields in stable wire order. */
  public static List<String> reversalFields() {
    return List.of(Reversal.PRIOR_POSTING_ID, Reversal.REASON);
  }

  /** Returns request-side tax-selection fields in stable wire order. */
  public static List<String> taxFields() {
    return List.of(Tax.TAX_REGISTRATION_ID, Tax.TAX_CODE);
  }

  /** Returns settlement-adjunct request fields in stable wire order. */
  public static List<String> settlementAdjunctFields() {
    return List.of(SettlementAdjunct.ACCOUNT_CODE, SettlementAdjunct.AMOUNT);
  }

  /** Returns inventory-relief request fields in stable wire order. */
  public static List<String> inventoryReliefFields() {
    return List.of(
        InventoryRelief.INVENTORY_ACCOUNT_CODE,
        InventoryRelief.COST_OF_SALES_ACCOUNT_CODE,
        InventoryRelief.QUANTITY);
  }

  /** Returns inclusive accrual cut-off recognition-interval request fields in stable wire order. */
  public static List<String> recognitionIntervalFields() {
    return List.of(RecognitionInterval.START_DATE, RecognitionInterval.END_DATE);
  }

  /** Returns fixed-asset depreciation-schedule request fields in stable wire order. */
  public static List<String> fixedAssetDepreciationScheduleFields() {
    return ProtocolFixedAssetRequestFields.depreciationScheduleFields();
  }

  /** Returns request-side foreign-exchange fields in stable wire order. */
  public static List<String> foreignExchangeFields() {
    return ProtocolForeignExchangeRequestFields.foreignExchangeFields();
  }

  /** Returns quoted-rate request fields in stable wire order. */
  public static List<String> quotedRateFields() {
    return ProtocolForeignExchangeRequestFields.quotedRateFields();
  }

  /** Journal-line request fields. */
  public static final class JournalLine {
    public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
    public static final String SIDE = "side";
    public static final String AMOUNT = "amount";

    private JournalLine() {}
  }

  /** Opening-balance request fields. */
  public static final class OpeningBalance {
    public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
    public static final String SIDE = "side";
    public static final String AMOUNT = "amount";
    public static final String QUANTITY = "quantity";

    private OpeningBalance() {}
  }

  /** Provenance request fields. */
  public static final class Provenance {
    public static final String ACTOR_ID = "actorId";
    public static final String ACTOR_TYPE = "actorType";
    public static final String COMMAND_ID = "commandId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String CAUSATION_ID = "causationId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String REASON = "reason";
    public static final String RECORDED_AT = "recordedAt";
    public static final String SOURCE_CHANNEL = "sourceChannel";

    private Provenance() {}
  }

  /** Evidence request fields. */
  public static final class Evidence {
    public static final String SOURCE_DOCUMENTS = "sourceDocuments";
    public static final String APPROVALS = "approvals";

    private Evidence() {}
  }

  /** Source-document evidence fields. */
  public static final class SourceDocument {
    public static final String SOURCE_DOCUMENT_ID = "sourceDocumentId";
    public static final String SOURCE_DOCUMENT_TYPE = "sourceDocumentType";
    public static final String DOCUMENT_DATE = "documentDate";

    private SourceDocument() {}
  }

  /** Approval evidence fields. */
  public static final class Approval {
    public static final String APPROVAL_ID = "approvalId";
    public static final String APPROVAL_TYPE = "approvalType";
    public static final String APPROVER_ID = "approverId";
    public static final String APPROVER_TYPE = "approverType";
    public static final String DECISION = "decision";
    public static final String APPROVED_AT = "approvedAt";

    private Approval() {}
  }

  /** Reversal request fields. */
  public static final class Reversal {
    public static final String PRIOR_POSTING_ID = "priorPostingId";
    public static final String REASON = "reason";
    public static final String KIND = "kind";

    private Reversal() {}
  }

  /** Request-side tax-selection fields. */
  public static final class Tax {
    public static final String TAX_REGISTRATION_ID =
        ProtocolTaxRegistrationFields.TAX_REGISTRATION_ID;
    public static final String TAX_CODE = ProtocolTaxRegistrationFields.TaxCode.TAX_CODE;

    private Tax() {}
  }

  /** Optional settlement-adjunct facts carried by receipt and payment requests. */
  public static final class SettlementAdjunct {
    public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
    public static final String AMOUNT = "amount";

    private SettlementAdjunct() {}
  }

  /** Optional trading-sale inventory-relief facts. */
  public static final class InventoryRelief {
    public static final String INVENTORY_ACCOUNT_CODE = "inventoryAccountCode";
    public static final String COST_OF_SALES_ACCOUNT_CODE = "costOfSalesAccountCode";
    public static final String QUANTITY = "quantity";

    private InventoryRelief() {}
  }

  /** Inclusive interval in which a deferred balance may be recognized. */
  public static final class RecognitionInterval {
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    private RecognitionInterval() {}
  }
}
