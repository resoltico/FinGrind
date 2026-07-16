package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared report-display semantics for statement classifications and section labels. */
final class ReportModelClassificationDisplay {
  private static final Map<FinancialPositionLineClassification, String> FINANCIAL_POSITION_LABELS =
      Map.ofEntries(
          Map.entry(FinancialPositionLineClassification.CURRENT_ASSET, "Current asset"),
          Map.entry(FinancialPositionLineClassification.INVENTORY, "Inventory"),
          Map.entry(FinancialPositionLineClassification.PREPAID_EXPENSE, "Prepaid expense"),
          Map.entry(FinancialPositionLineClassification.NONCURRENT_ASSET, "Non-current asset"),
          Map.entry(FinancialPositionLineClassification.TRADE_RECEIVABLE, "Trade receivable"),
          Map.entry(FinancialPositionLineClassification.CURRENT_LIABILITY, "Current liability"),
          Map.entry(
              FinancialPositionLineClassification.NONCURRENT_LIABILITY, "Non-current liability"),
          Map.entry(FinancialPositionLineClassification.TRADE_PAYABLE, "Trade payable"),
          Map.entry(FinancialPositionLineClassification.DEFERRED_REVENUE, "Deferred revenue"),
          Map.entry(FinancialPositionLineClassification.ACCRUED_EXPENSE, "Accrued expense"),
          Map.entry(FinancialPositionLineClassification.EQUITY_CONTRIBUTION, "Contributed capital"),
          Map.entry(FinancialPositionLineClassification.EQUITY_WITHDRAWAL, "Distributions"),
          Map.entry(FinancialPositionLineClassification.RESULT_HOLDING, "Accumulated result"),
          Map.entry(
              FinancialPositionLineClassification.RETAINED_ACCUMULATED, "Retained accumulated"),
          Map.entry(FinancialPositionLineClassification.RESERVE, "Reserve"),
          Map.entry(FinancialPositionLineClassification.OTHER_EQUITY, "Other equity"));

  private ReportModelClassificationDisplay() {}

  static String displayAccountTypeSection(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Assets";
      case LIABILITY -> "Liabilities";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expenses";
    };
  }

  static String displayFinancialPositionClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(ReportModelClassificationDisplay::displayFinancialPositionClassification)
        .orElse("Calculated line");
  }

  static String displayFinancialPositionClassification(
      FinancialPositionLineClassification lineClassification) {
    return Objects.requireNonNull(
        FINANCIAL_POSITION_LABELS.get(
            Objects.requireNonNull(lineClassification, "lineClassification")));
  }

  static String displayProfitAndLossClassification(
      ProfitAndLossLineClassification lineClassification) {
    return switch (lineClassification) {
      case OPERATING_REVENUE -> "Operating revenue";
      case SALES_DISCOUNT_ALLOWANCE -> "Sales discount allowance";
      case OTHER_REVENUE -> "Other revenue";
      case FINANCE_INCOME -> "Finance income";
      case COST_OF_SALES -> "Cost of sales";
      case OPERATING_EXPENSE -> "Operating expense";
      case DEPRECIATION_AND_AMORTIZATION -> "Depreciation and amortization";
      case SETTLEMENT_FEE -> "Settlement fee";
      case BAD_DEBT_WRITE_OFF -> "Bad debt write-off";
      case FINANCE_EXPENSE -> "Finance expense";
      case OTHER_EXPENSE -> "Other expense";
    };
  }

  static String displayCashFlowSection(CashFlowSectionKind sectionKind) {
    return switch (sectionKind) {
      case OPERATING -> "Operating";
      case INVESTING -> "Investing";
      case FINANCING -> "Financing";
    };
  }

  static String displayCashFlowClassification(
      AccountType lineType,
      Optional<FinancialPositionLineClassification> financialPositionLineClassification,
      Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
    String detailedLabel =
        financialPositionLineClassification
            .map(ReportModelClassificationDisplay::displayFinancialPositionClassification)
            .or(
                () ->
                    profitAndLossLineClassification.map(
                        ReportModelClassificationDisplay::displayProfitAndLossClassification))
            .orElse("Calculated line");
    return ReportModelDisplay.displayLineType(lineType) + " (" + detailedLabel + ")";
  }
}
