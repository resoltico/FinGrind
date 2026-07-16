package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared operator-facing labels for account and statement vocabulary. */
final class CliAccountStatementLabels {
  private static final Map<FinancialPositionLineClassification, String>
      FINANCIAL_POSITION_LINE_LABELS =
          Map.ofEntries(
              Map.entry(FinancialPositionLineClassification.CURRENT_ASSET, "Current asset"),
              Map.entry(FinancialPositionLineClassification.INVENTORY, "Inventory"),
              Map.entry(FinancialPositionLineClassification.PREPAID_EXPENSE, "Prepaid expense"),
              Map.entry(FinancialPositionLineClassification.NONCURRENT_ASSET, "Non-current asset"),
              Map.entry(FinancialPositionLineClassification.TRADE_RECEIVABLE, "Trade receivable"),
              Map.entry(FinancialPositionLineClassification.CURRENT_LIABILITY, "Current liability"),
              Map.entry(
                  FinancialPositionLineClassification.NONCURRENT_LIABILITY,
                  "Non-current liability"),
              Map.entry(FinancialPositionLineClassification.TRADE_PAYABLE, "Trade payable"),
              Map.entry(FinancialPositionLineClassification.DEFERRED_REVENUE, "Deferred revenue"),
              Map.entry(FinancialPositionLineClassification.ACCRUED_EXPENSE, "Accrued expense"),
              Map.entry(
                  FinancialPositionLineClassification.EQUITY_CONTRIBUTION, "Contributed capital"),
              Map.entry(FinancialPositionLineClassification.EQUITY_WITHDRAWAL, "Distributions"),
              Map.entry(FinancialPositionLineClassification.RESULT_HOLDING, "Accumulated result"),
              Map.entry(
                  FinancialPositionLineClassification.RETAINED_ACCUMULATED, "Retained accumulated"),
              Map.entry(FinancialPositionLineClassification.RESERVE, "Reserve"),
              Map.entry(FinancialPositionLineClassification.OTHER_EQUITY, "Other equity"));
  private static final Map<ProfitAndLossLineClassification, String> PROFIT_AND_LOSS_LINE_LABELS =
      Map.ofEntries(
          Map.entry(ProfitAndLossLineClassification.OPERATING_REVENUE, "Operating revenue"),
          Map.entry(
              ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE, "Sales discount allowance"),
          Map.entry(ProfitAndLossLineClassification.OTHER_REVENUE, "Other revenue"),
          Map.entry(ProfitAndLossLineClassification.FINANCE_INCOME, "Finance income"),
          Map.entry(ProfitAndLossLineClassification.COST_OF_SALES, "Cost of sales"),
          Map.entry(ProfitAndLossLineClassification.OPERATING_EXPENSE, "Operating expense"),
          Map.entry(
              ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION,
              "Depreciation and amortization"),
          Map.entry(ProfitAndLossLineClassification.SETTLEMENT_FEE, "Settlement fee"),
          Map.entry(ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF, "Bad debt write-off"),
          Map.entry(ProfitAndLossLineClassification.FINANCE_EXPENSE, "Finance expense"),
          Map.entry(ProfitAndLossLineClassification.OTHER_EXPENSE, "Other expense"));

  private CliAccountStatementLabels() {}

  static String displayAccountTypeSectionLabel(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Assets";
      case LIABILITY -> "Liabilities";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expenses";
    };
  }

  static String displayLineTypeLabel(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Asset";
      case LIABILITY -> "Liability";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expense";
    };
  }

  static String displayRowKind(StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> "Account";
      case CURRENT_PERIOD_RESULT -> "Current period result";
    };
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> lineCode;
      case CURRENT_PERIOD_RESULT -> CliHumanDisplay.calculatedLineLabel();
    };
  }

  static String displayAccountNodeKindLabel(AccountNodeKind nodeKind) {
    return switch (nodeKind) {
      case HEADER -> "Header";
      case POSTABLE -> "Postable";
    };
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    return requireMappedLabel(
        FINANCIAL_POSITION_LINE_LABELS, lineClassification, "financial position classification");
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(CliAccountStatementLabels::displayFinancialPositionLineClassification)
        .orElse(CliHumanDisplay.calculatedLineLabel());
  }

  static String displayProfitAndLossLineClassification(
      ProfitAndLossLineClassification lineClassification) {
    return requireMappedLabel(
        PROFIT_AND_LOSS_LINE_LABELS, lineClassification, "profit and loss classification");
  }

  static String displayCashFlowAssetClassification(
      CashFlowAssetClassification cashFlowAssetClassification) {
    return switch (cashFlowAssetClassification) {
      case CASH_AND_CASH_EQUIVALENT -> "Cash and cash equivalents";
      case NON_CASH -> "Non-cash asset";
    };
  }

  static String displayCashFlowSectionLabel(CashFlowSectionKind sectionKind) {
    return switch (sectionKind) {
      case OPERATING -> "Operating";
      case INVESTING -> "Investing";
      case FINANCING -> "Financing";
    };
  }

  static String displayNormalBalanceLabel(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  private static <K> String requireMappedLabel(Map<K, String> labels, K key, String labelFamily) {
    Objects.requireNonNull(key, labelFamily);
    return Objects.requireNonNull(labels.get(key));
  }
}
