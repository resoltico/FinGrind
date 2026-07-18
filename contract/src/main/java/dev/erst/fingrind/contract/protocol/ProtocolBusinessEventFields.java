package dev.erst.fingrind.contract.protocol;

/** Canonical top-level posting fields grouped by the business event that owns their meaning. */
public interface ProtocolBusinessEventFields {

  /** Posting facts shared by more than one business-event family. */
  public static final class Core {
    public static final String ENTRY_KIND = "entryKind";
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String CASH_ACCOUNT_CODE = "cashAccountCode";
    public static final String RECEIVABLE_ACCOUNT_CODE = "receivableAccountCode";
    public static final String PAYABLE_ACCOUNT_CODE = "payableAccountCode";
    public static final String REVENUE_ACCOUNT_CODE = "revenueAccountCode";
    public static final String EQUITY_ACCOUNT_CODE = "equityAccountCode";
    public static final String AMOUNT = "amount";
    public static final String SETTLEMENT_ADJUNCT = "settlementAdjunct";
    public static final String FOREIGN_EXCHANGE = "foreignExchange";
    public static final String TAX = "tax";
    public static final String LINES = "lines";
    public static final String OPENING_BALANCES = "openingBalances";
    public static final String EVIDENCE = "evidence";
    public static final String PROVENANCE = "provenance";
    public static final String REVERSAL = "reversal";

    private Core() {}
  }

  /** Accrual cut-off posting facts. */
  public static final class AccrualCutoff {
    public static final String ACCRUAL_CUTOFF_ID = "accrualCutoffId";
    public static final String PREPAYMENT_ASSET_ACCOUNT_CODE = "prepaymentAssetAccountCode";
    public static final String DEFERRED_REVENUE_ACCOUNT_CODE = "deferredRevenueAccountCode";
    public static final String ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE =
        "accruedExpenseLiabilityAccountCode";
    public static final String RECOGNITION_INTERVAL = "recognitionInterval";

    private AccrualCutoff() {}
  }

  /** Fixed-asset posting facts. */
  public static final class FixedAsset {
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

    private FixedAsset() {}
  }

  /** Financing posting facts. */
  public static final class Financing {
    public static final String FINANCING_ARRANGEMENT_ID = "financingArrangementId";
    public static final String PRINCIPAL_LIABILITY_ACCOUNT_CODE = "principalLiabilityAccountCode";
    public static final String INTEREST_PAYABLE_ACCOUNT_CODE = "interestPayableAccountCode";
    public static final String INTEREST_EXPENSE_ACCOUNT_CODE = "interestExpenseAccountCode";
    public static final String PRINCIPAL_AMOUNT = "principalAmount";
    public static final String INTEREST_AMOUNT = "interestAmount";

    private Financing() {}
  }

  /** Realized foreign-exchange posting facts. */
  public static final class RealizedForeignExchange {
    public static final String FOREIGN_CURRENCY_OBLIGATION_ID = "foreignCurrencyObligationId";
    public static final String REALIZED_GAIN_ACCOUNT_CODE = "realizedGainAccountCode";
    public static final String REALIZED_LOSS_ACCOUNT_CODE = "realizedLossAccountCode";

    private RealizedForeignExchange() {}
  }

  /** Inventory posting facts. */
  public static final class Inventory {
    public static final String INVENTORY_ACCOUNT_CODE = "inventoryAccountCode";
    public static final String EXPENSE_ACCOUNT_CODE = "expenseAccountCode";
    public static final String WRITE_DOWN_LOSS_ACCOUNT_CODE = "writeDownLossAccountCode";
    public static final String SHRINKAGE_LOSS_ACCOUNT_CODE = "shrinkageLossAccountCode";
    public static final String COUNT_GAIN_ACCOUNT_CODE = "countGainAccountCode";
    public static final String QUANTITY = "quantity";
    public static final String UNIT_COST = "unitCost";
    public static final String INVENTORY_RELIEF = "inventoryRelief";

    private Inventory() {}
  }

  /** Latvian payroll posting facts. */
  public static final class LatvianPayroll {
    public static final String PAYROLL_RUN_ID = "payrollRunId";
    public static final String EMPLOYEE_REFERENCE = "employeeReference";
    public static final String PAYROLL_MONTH = "payrollMonth";
    public static final String TAX_BOOK_HELD_AT_EMPLOYER = "taxBookHeldAtEmployer";
    public static final String DEPENDANT_COUNT = "dependantCount";
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

    private LatvianPayroll() {}
  }
}
