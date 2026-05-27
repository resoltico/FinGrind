package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.List;
import java.util.Optional;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
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

  static String displayRowKind(StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> "Account";
      case CURRENT_PERIOD_RESULT -> "Current period result";
    };
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> lineCode;
      case CURRENT_PERIOD_RESULT -> "(derived)";
    };
  }

  static String displayLineRole(Optional<AccountRole> lineRole) {
    return lineRole.map(PdfValueFormatter::displayAccountRole).orElse("(derived)");
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

  static String displayAccountRole(AccountRole accountRole) {
    return switch (accountRole) {
      case ORDINARY -> "Ordinary";
      case CONTRA -> "Contra";
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
      case RESERVE -> "Reserve";
      case OTHER_EQUITY -> "Other equity";
    };
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(PdfValueFormatter::displayFinancialPositionLineClassification)
        .orElse("(derived)");
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
      case OTHER_EXPENSE -> "Tax expense";
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

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-transfer postings";
    };
  }

  static String displayBusinessActivityTags(List<BusinessActivityTag> businessActivityTags) {
    return businessActivityTags.isEmpty()
        ? "(none)"
        : businessActivityTags.stream()
            .map(BusinessActivityTag::value)
            .collect(java.util.stream.Collectors.joining(", "));
  }

  static String reversalTarget(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(not a reversal)");
  }

  static String postingRole(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_RESULT_TRANSFER -> "Period result transfer";
      case OPENING_BALANCE -> "Opening balance";
    };
  }
}
