package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Optional;

/** Shared operator-facing labels for account and statement vocabulary. */
final class CliAccountStatementLabels {
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
    return switch (lineClassification) {
      case CURRENT_ASSET -> "Current asset";
      case NONCURRENT_ASSET -> "Non-current asset";
      case CURRENT_LIABILITY -> "Current liability";
      case NONCURRENT_LIABILITY -> "Non-current liability";
      case EQUITY_CONTRIBUTION -> "Contributed capital";
      case EQUITY_WITHDRAWAL -> "Distributions";
      case RESULT_HOLDING -> "Accumulated result";
      case RETAINED_ACCUMULATED -> "Retained accumulated";
      case RESERVE -> "Reserve";
      case OTHER_EQUITY -> "Other equity";
    };
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(CliAccountStatementLabels::displayFinancialPositionLineClassification)
        .orElse(CliHumanDisplay.calculatedLineLabel());
  }

  static String displayProfitAndLossLineClassification(
      ProfitAndLossLineClassification lineClassification) {
    return switch (lineClassification) {
      case OPERATING_REVENUE -> "Operating revenue";
      case OTHER_REVENUE -> "Other revenue";
      case FINANCE_INCOME -> "Finance income";
      case COST_OF_SALES -> "Cost of sales";
      case OPERATING_EXPENSE -> "Operating expense";
      case DEPRECIATION_AND_AMORTIZATION -> "Depreciation and amortization";
      case FINANCE_EXPENSE -> "Finance expense";
      case OTHER_EXPENSE -> "Other expense";
    };
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
}
