package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
  private static final Map<FinancialPositionLineClassification, String>
      FINANCIAL_POSITION_LINE_CLASSIFICATIONS =
          Map.ofEntries(
              Map.entry(FinancialPositionLineClassification.CURRENT_ASSET, "Current asset"),
              Map.entry(FinancialPositionLineClassification.NONCURRENT_ASSET, "Non-current asset"),
              Map.entry(FinancialPositionLineClassification.INVENTORY, "Inventory"),
              Map.entry(FinancialPositionLineClassification.PREPAID_EXPENSE, "Prepaid expense"),
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

  private PdfValueFormatter() {}

  static String displayMoney(Money money) {
    return money.canonicalDecimal();
  }

  static String displayBalanceSide(BalanceSide balanceSide) {
    return switch (balanceSide) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
      case ZERO -> "Balanced";
    };
  }

  static String displayAccountTypeSection(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Assets";
      case LIABILITY -> "Liabilities";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expenses";
    };
  }

  static String displayCashFlowSection(CashFlowSectionKind sectionKind) {
    return switch (sectionKind) {
      case OPERATING -> "Operating";
      case INVESTING -> "Investing";
      case FINANCING -> "Financing";
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
      case CURRENT_PERIOD_RESULT -> "Calculated line";
    };
  }

  static String displayAccountType(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Asset";
      case LIABILITY -> "Liability";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expense";
    };
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    Objects.requireNonNull(lineClassification, "lineClassification");
    return Objects.requireNonNull(FINANCIAL_POSITION_LINE_CLASSIFICATIONS.get(lineClassification));
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(PdfValueFormatter::displayFinancialPositionLineClassification)
        .orElse("Calculated line");
  }

  static String displayProfitAndLossLineClassification(
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

  static String displayNormalBalance(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  static String displayBoolean(boolean value) {
    return value ? "Yes" : "No";
  }
}
