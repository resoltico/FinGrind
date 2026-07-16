package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Public JSON payload for one caller-authored posting entry. */
public record CliPostingEntryPayload(
    String entryKind,
    @Nullable String cashAccountCode,
    @Nullable String receivableAccountCode,
    @Nullable String payableAccountCode,
    @Nullable String revenueAccountCode,
    @Nullable String inventoryAccountCode,
    @Nullable String expenseAccountCode,
    @Nullable String writeDownLossAccountCode,
    @Nullable String shrinkageLossAccountCode,
    @Nullable String countGainAccountCode,
    @Nullable String equityAccountCode,
    @Nullable MonetaryAmount amount,
    @Nullable String quantity,
    @Nullable MonetaryAmount unitCost,
    CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief,
    @Nullable SettlementAdjunctPayload settlementAdjunct,
    CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange,
    CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection,
    CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
    CliBookQueryJsonModels.@Nullable ReversalPayload reversal,
    @Nullable List<CliOpeningBalancePayload> openingBalances,
    @Nullable ResolvedInventoryCostingPayload resolvedInventoryCosting,
    @Nullable AccrualCutoffPayload accrualCutoff,
    @Nullable LatvianMonthlyPayrollPayload latvianMonthlyPayroll,
    @Nullable LatvianPayrollSettlementPayload latvianPayrollSettlement,
    CliFixedAssetPostingJsonModels.@Nullable FixedAssetPayload fixedAsset) {
  /** Validates one caller-authored posting entry payload. */
  public CliPostingEntryPayload {
    entryKind = requireText(entryKind, "entryKind");
    cashAccountCode = requireOptionalText(cashAccountCode, "cashAccountCode");
    receivableAccountCode = requireOptionalText(receivableAccountCode, "receivableAccountCode");
    payableAccountCode = requireOptionalText(payableAccountCode, "payableAccountCode");
    revenueAccountCode = requireOptionalText(revenueAccountCode, "revenueAccountCode");
    inventoryAccountCode = requireOptionalText(inventoryAccountCode, "inventoryAccountCode");
    expenseAccountCode = requireOptionalText(expenseAccountCode, "expenseAccountCode");
    writeDownLossAccountCode =
        requireOptionalText(writeDownLossAccountCode, "writeDownLossAccountCode");
    shrinkageLossAccountCode =
        requireOptionalText(shrinkageLossAccountCode, "shrinkageLossAccountCode");
    countGainAccountCode = requireOptionalText(countGainAccountCode, "countGainAccountCode");
    equityAccountCode = requireOptionalText(equityAccountCode, "equityAccountCode");
    quantity = requireOptionalText(quantity, "quantity");
    openingBalances = openingBalances == null ? null : copyList(openingBalances, "openingBalances");
  }

  /** Public JSON payload for one optional settlement-adjunct line. */
  public record SettlementAdjunctPayload(String accountCode, MonetaryAmount amount) {
    public SettlementAdjunctPayload {
      accountCode = requireText(accountCode, "accountCode");
      Objects.requireNonNull(amount, "amount");
    }
  }

  /** Public JSON payload for one optional trading-sale inventory-relief bundle. */
  public record InventoryReliefPayload(
      String inventoryAccountCode, String costOfSalesAccountCode, String quantity) {
    public InventoryReliefPayload {
      inventoryAccountCode = requireText(inventoryAccountCode, "inventoryAccountCode");
      costOfSalesAccountCode = requireText(costOfSalesAccountCode, "costOfSalesAccountCode");
      quantity = requireText(quantity, "quantity");
    }
  }

  /** Executor-derived sale costing facts retained for committed-posting transparency. */
  public record ResolvedInventoryCostingPayload(
      MonetaryAmount costOfSales,
      String quantityRelieved,
      MonetaryAmount roundedMovingAverageUnitCostProjection) {
    public ResolvedInventoryCostingPayload {
      Objects.requireNonNull(costOfSales, "costOfSales");
      quantityRelieved = requireText(quantityRelieved, "quantityRelieved");
      Objects.requireNonNull(
          roundedMovingAverageUnitCostProjection, "roundedMovingAverageUnitCostProjection");
    }
  }

  /** Caller facts and executor-derived components for one Latvian monthly payroll run. */
  public record LatvianMonthlyPayrollPayload(
      String payrollRunId,
      String employeeReference,
      String payrollMonth,
      String wageExpenseAccountCode,
      String employerSocialContributionExpenseAccountCode,
      String netWagesPayableAccountCode,
      String employeeSocialContributionPayableAccountCode,
      String employerSocialContributionPayableAccountCode,
      String personalIncomeTaxPayableAccountCode,
      MonetaryAmount grossWages,
      @Nullable ResolvedLatvianMonthlyPayrollCalculationPayload resolvedCalculation) {
    public LatvianMonthlyPayrollPayload {
      payrollRunId = requireText(payrollRunId, "payrollRunId");
      employeeReference = requireText(employeeReference, "employeeReference");
      payrollMonth = requireText(payrollMonth, "payrollMonth");
      wageExpenseAccountCode = requireText(wageExpenseAccountCode, "wageExpenseAccountCode");
      employerSocialContributionExpenseAccountCode =
          requireText(
              employerSocialContributionExpenseAccountCode,
              "employerSocialContributionExpenseAccountCode");
      netWagesPayableAccountCode =
          requireText(netWagesPayableAccountCode, "netWagesPayableAccountCode");
      employeeSocialContributionPayableAccountCode =
          requireText(
              employeeSocialContributionPayableAccountCode,
              "employeeSocialContributionPayableAccountCode");
      employerSocialContributionPayableAccountCode =
          requireText(
              employerSocialContributionPayableAccountCode,
              "employerSocialContributionPayableAccountCode");
      personalIncomeTaxPayableAccountCode =
          requireText(personalIncomeTaxPayableAccountCode, "personalIncomeTaxPayableAccountCode");
      Objects.requireNonNull(grossWages, "grossWages");
    }
  }

  /** Caller facts and executor-derived components for one exact payroll-settlement obligation. */
  public record LatvianPayrollSettlementPayload(
      String settlementKind,
      String payrollRunId,
      String cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlementPayload resolvedSettlement) {
    public LatvianPayrollSettlementPayload {
      settlementKind = requireText(settlementKind, "settlementKind");
      payrollRunId = requireText(payrollRunId, "payrollRunId");
      cashAccountCode = requireText(cashAccountCode, "cashAccountCode");
    }
  }

  /** Exact payroll-run components retained as the executor-resolved settlement journal facts. */
  public record ResolvedLatvianPayrollSettlementPayload(
      String netWagesPayableAccountCode,
      String employeeSocialContributionPayableAccountCode,
      String employerSocialContributionPayableAccountCode,
      String personalIncomeTaxPayableAccountCode,
      MonetaryAmount netWages,
      MonetaryAmount employeeSocialContribution,
      MonetaryAmount employerSocialContribution,
      MonetaryAmount personalIncomeTax) {
    public ResolvedLatvianPayrollSettlementPayload {
      netWagesPayableAccountCode =
          requireText(netWagesPayableAccountCode, "netWagesPayableAccountCode");
      employeeSocialContributionPayableAccountCode =
          requireText(
              employeeSocialContributionPayableAccountCode,
              "employeeSocialContributionPayableAccountCode");
      employerSocialContributionPayableAccountCode =
          requireText(
              employerSocialContributionPayableAccountCode,
              "employerSocialContributionPayableAccountCode");
      personalIncomeTaxPayableAccountCode =
          requireText(personalIncomeTaxPayableAccountCode, "personalIncomeTaxPayableAccountCode");
      Objects.requireNonNull(netWages, "netWages");
      Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
      Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
      Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
    }
  }

  /** Executor-derived statutory component amounts for one admitted payroll run. */
  public record ResolvedLatvianMonthlyPayrollCalculationPayload(
      MonetaryAmount employeeSocialContribution,
      MonetaryAmount employerSocialContribution,
      MonetaryAmount monthlyNonTaxableMinimum,
      MonetaryAmount personalIncomeTax,
      MonetaryAmount netWages) {
    public ResolvedLatvianMonthlyPayrollCalculationPayload {
      Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
      Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
      Objects.requireNonNull(monthlyNonTaxableMinimum, "monthlyNonTaxableMinimum");
      Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
      Objects.requireNonNull(netWages, "netWages");
    }
  }

  /**
   * Durable aggregate facts and executor-resolved lifecycle facts for one accrual cut-off entry.
   */
  public record AccrualCutoffPayload(
      String accrualCutoffId,
      @Nullable String aggregateKind,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      @Nullable RecognitionIntervalPayload recognitionInterval,
      @Nullable ResolvedApplicationPayload resolvedApplication) {
    public AccrualCutoffPayload {
      accrualCutoffId = requireText(accrualCutoffId, "accrualCutoffId");
      aggregateKind = requireOptionalText(aggregateKind, "aggregateKind");
      prepaymentAssetAccountCode =
          requireOptionalText(prepaymentAssetAccountCode, "prepaymentAssetAccountCode");
      deferredRevenueAccountCode =
          requireOptionalText(deferredRevenueAccountCode, "deferredRevenueAccountCode");
      accruedExpenseLiabilityAccountCode =
          requireOptionalText(
              accruedExpenseLiabilityAccountCode, "accruedExpenseLiabilityAccountCode");
      if (aggregateKind == null && resolvedApplication == null) {
        throw new IllegalArgumentException(
            "An accrual cut-off payload must publish aggregate facts or a resolved application.");
      }
      if (aggregateKind != null && resolvedApplication != null) {
        throw new IllegalArgumentException(
            "An accrual cut-off payload must not combine aggregate facts with a resolved application.");
      }
    }
  }

  /** Inclusive recognition interval for one deferred cut-off balance. */
  public record RecognitionIntervalPayload(String startDate, String endDate) {
    public RecognitionIntervalPayload {
      startDate = requireText(startDate, "startDate");
      endDate = requireText(endDate, "endDate");
    }
  }

  /** Account-pair facts resolved from the persisted accrual cut-off aggregate. */
  public record ResolvedApplicationPayload(
      String applicationKind, String debitAccountCode, String creditAccountCode) {
    public ResolvedApplicationPayload {
      applicationKind = requireText(applicationKind, "applicationKind");
      debitAccountCode = requireText(debitAccountCode, "debitAccountCode");
      creditAccountCode = requireText(creditAccountCode, "creditAccountCode");
    }
  }
}
