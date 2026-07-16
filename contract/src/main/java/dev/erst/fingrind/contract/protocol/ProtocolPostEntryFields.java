package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical post-entry request field names shared by parser and machine contract surfaces. */
public final class ProtocolPostEntryFields {
  private ProtocolPostEntryFields() {}

  /** Returns top-level posting request fields in stable wire order. */
  public static List<String> topLevelFields() {
    return List.of(
        TopLevel.ENTRY_KIND,
        TopLevel.EFFECTIVE_DATE,
        TopLevel.CASH_ACCOUNT_CODE,
        TopLevel.RECEIVABLE_ACCOUNT_CODE,
        TopLevel.PAYABLE_ACCOUNT_CODE,
        TopLevel.REVENUE_ACCOUNT_CODE,
        TopLevel.ACCRUAL_CUTOFF_ID,
        TopLevel.FIXED_ASSET_ID,
        TopLevel.ASSET_ACCOUNT_CODE,
        TopLevel.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE,
        TopLevel.DEPRECIATION_EXPENSE_ACCOUNT_CODE,
        TopLevel.DISPOSAL_GAIN_ACCOUNT_CODE,
        TopLevel.DISPOSAL_LOSS_ACCOUNT_CODE,
        TopLevel.COST,
        TopLevel.DEPRECIATION_SCHEDULE,
        TopLevel.PROCEEDS,
        TopLevel.FINANCING_ARRANGEMENT_ID,
        TopLevel.PRINCIPAL_LIABILITY_ACCOUNT_CODE,
        TopLevel.INTEREST_PAYABLE_ACCOUNT_CODE,
        TopLevel.INTEREST_EXPENSE_ACCOUNT_CODE,
        TopLevel.PRINCIPAL_AMOUNT,
        TopLevel.INTEREST_AMOUNT,
        TopLevel.FOREIGN_CURRENCY_OBLIGATION_ID,
        TopLevel.REALIZED_GAIN_ACCOUNT_CODE,
        TopLevel.REALIZED_LOSS_ACCOUNT_CODE,
        TopLevel.PREPAYMENT_ASSET_ACCOUNT_CODE,
        TopLevel.DEFERRED_REVENUE_ACCOUNT_CODE,
        TopLevel.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
        TopLevel.INVENTORY_ACCOUNT_CODE,
        TopLevel.EXPENSE_ACCOUNT_CODE,
        TopLevel.WRITE_DOWN_LOSS_ACCOUNT_CODE,
        TopLevel.SHRINKAGE_LOSS_ACCOUNT_CODE,
        TopLevel.COUNT_GAIN_ACCOUNT_CODE,
        TopLevel.EQUITY_ACCOUNT_CODE,
        TopLevel.PAYROLL_RUN_ID,
        TopLevel.EMPLOYEE_REFERENCE,
        TopLevel.PAYROLL_MONTH,
        TopLevel.WAGE_EXPENSE_ACCOUNT_CODE,
        TopLevel.EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE,
        TopLevel.NET_WAGES_PAYABLE_ACCOUNT_CODE,
        TopLevel.EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
        TopLevel.EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
        TopLevel.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE,
        TopLevel.GROSS_WAGES,
        TopLevel.AMOUNT,
        TopLevel.QUANTITY,
        TopLevel.UNIT_COST,
        TopLevel.RECOGNITION_INTERVAL,
        TopLevel.INVENTORY_RELIEF,
        TopLevel.SETTLEMENT_ADJUNCT,
        TopLevel.FOREIGN_EXCHANGE,
        TopLevel.TAX,
        TopLevel.LINES,
        TopLevel.OPENING_BALANCES,
        TopLevel.EVIDENCE,
        TopLevel.PROVENANCE,
        TopLevel.REVERSAL);
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

  /** Top-level posting request fields. */
  public static final class TopLevel {
    public static final String ENTRY_KIND = "entryKind";
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String CASH_ACCOUNT_CODE = "cashAccountCode";
    public static final String RECEIVABLE_ACCOUNT_CODE = "receivableAccountCode";
    public static final String PAYABLE_ACCOUNT_CODE = "payableAccountCode";
    public static final String REVENUE_ACCOUNT_CODE = "revenueAccountCode";
    public static final String ACCRUAL_CUTOFF_ID = "accrualCutoffId";
    public static final String FIXED_ASSET_ID = "fixedAssetId";
    public static final String ASSET_ACCOUNT_CODE = "assetAccountCode";
    public static final String ACCUMULATED_DEPRECIATION_ACCOUNT_CODE =
        "accumulatedDepreciationAccountCode";
    public static final String DEPRECIATION_EXPENSE_ACCOUNT_CODE = "depreciationExpenseAccountCode";
    public static final String DISPOSAL_GAIN_ACCOUNT_CODE = "disposalGainAccountCode";
    public static final String DISPOSAL_LOSS_ACCOUNT_CODE = "disposalLossAccountCode";
    public static final String COST = "cost";
    public static final String DEPRECIATION_SCHEDULE = "depreciationSchedule";
    public static final String PROCEEDS = "proceeds";
    public static final String FINANCING_ARRANGEMENT_ID = "financingArrangementId";
    public static final String PRINCIPAL_LIABILITY_ACCOUNT_CODE = "principalLiabilityAccountCode";
    public static final String INTEREST_PAYABLE_ACCOUNT_CODE = "interestPayableAccountCode";
    public static final String INTEREST_EXPENSE_ACCOUNT_CODE = "interestExpenseAccountCode";
    public static final String PRINCIPAL_AMOUNT = "principalAmount";
    public static final String INTEREST_AMOUNT = "interestAmount";
    public static final String FOREIGN_CURRENCY_OBLIGATION_ID = "foreignCurrencyObligationId";
    public static final String REALIZED_GAIN_ACCOUNT_CODE = "realizedGainAccountCode";
    public static final String REALIZED_LOSS_ACCOUNT_CODE = "realizedLossAccountCode";
    public static final String PREPAYMENT_ASSET_ACCOUNT_CODE = "prepaymentAssetAccountCode";
    public static final String DEFERRED_REVENUE_ACCOUNT_CODE = "deferredRevenueAccountCode";
    public static final String ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE =
        "accruedExpenseLiabilityAccountCode";
    public static final String INVENTORY_ACCOUNT_CODE = "inventoryAccountCode";
    public static final String EXPENSE_ACCOUNT_CODE = "expenseAccountCode";
    public static final String WRITE_DOWN_LOSS_ACCOUNT_CODE = "writeDownLossAccountCode";
    public static final String SHRINKAGE_LOSS_ACCOUNT_CODE = "shrinkageLossAccountCode";
    public static final String COUNT_GAIN_ACCOUNT_CODE = "countGainAccountCode";
    public static final String EQUITY_ACCOUNT_CODE = "equityAccountCode";
    public static final String PAYROLL_RUN_ID = "payrollRunId";
    public static final String EMPLOYEE_REFERENCE = "employeeReference";
    public static final String PAYROLL_MONTH = "payrollMonth";
    public static final String WAGE_EXPENSE_ACCOUNT_CODE = "wageExpenseAccountCode";
    public static final String EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE =
        "employerSocialContributionExpenseAccountCode";
    public static final String NET_WAGES_PAYABLE_ACCOUNT_CODE = "netWagesPayableAccountCode";
    public static final String EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE =
        "employeeSocialContributionPayableAccountCode";
    public static final String EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE =
        "employerSocialContributionPayableAccountCode";
    public static final String PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE =
        "personalIncomeTaxPayableAccountCode";
    public static final String GROSS_WAGES = "grossWages";
    public static final String AMOUNT = "amount";
    public static final String QUANTITY = "quantity";
    public static final String UNIT_COST = "unitCost";
    public static final String RECOGNITION_INTERVAL = "recognitionInterval";
    public static final String INVENTORY_RELIEF = "inventoryRelief";
    public static final String SETTLEMENT_ADJUNCT = "settlementAdjunct";
    public static final String FOREIGN_EXCHANGE = "foreignExchange";
    public static final String TAX = "tax";
    public static final String LINES = "lines";
    public static final String OPENING_BALANCES = "openingBalances";
    public static final String EVIDENCE = "evidence";
    public static final String PROVENANCE = "provenance";
    public static final String REVERSAL = "reversal";

    private TopLevel() {}
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
