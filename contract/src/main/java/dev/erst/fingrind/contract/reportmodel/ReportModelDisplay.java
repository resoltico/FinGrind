package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Map;

/** Shared report-display semantics for labels, ranges, and human-readable values. */
final class ReportModelDisplay {
  private static final Map<PostingOriginKind, String> POSTING_ORIGIN_LABELS =
      Map.ofEntries(
          Map.entry(PostingOriginKind.DIRECT_JOURNAL, "Direct journal"),
          Map.entry(PostingOriginKind.SALE_SETTLED, "Settled sale"),
          Map.entry(PostingOriginKind.SALE_ON_CREDIT, "Sale on credit"),
          Map.entry(PostingOriginKind.PURCHASE_SETTLED, "Settled purchase"),
          Map.entry(PostingOriginKind.PURCHASE_ON_CREDIT, "Purchase on credit"),
          Map.entry(PostingOriginKind.EXPENSE_SETTLED, "Settled expense"),
          Map.entry(PostingOriginKind.EXPENSE_ON_CREDIT, "Expense on credit"),
          Map.entry(PostingOriginKind.RECEIPT, "Receipt"),
          Map.entry(PostingOriginKind.PAYMENT, "Payment"),
          Map.entry(PostingOriginKind.OWNER_CONTRIBUTION, "Owner contribution"),
          Map.entry(PostingOriginKind.OWNER_WITHDRAWAL, "Owner withdrawal"),
          Map.entry(PostingOriginKind.OPENING_POSITION, "Opening position"),
          Map.entry(PostingOriginKind.REVERSAL, "Reversal"),
          Map.entry(PostingOriginKind.INTERIM_RESULT_SWEEP, "Interim result sweep"),
          Map.entry(PostingOriginKind.FISCAL_YEAR_CLOSE, "Fiscal-year close"));

  private ReportModelDisplay() {}

  static String displayMoney(Money money) {
    return money.currencyUnit().code() + " " + money.canonicalDecimal();
  }

  static String displayAmount(MonetaryAmount amount) {
    return amount.currencyCode() + " " + amount.canonicalDecimal();
  }

  static String displayBoolean(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayBalanceSide(BalanceSide balanceSide) {
    return switch (balanceSide) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
      case ZERO -> "Zero";
    };
  }

  static String displayBalanceState(boolean balanced) {
    return balanced ? "Balanced" : "Imbalanced";
  }

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-close postings";
    };
  }

  static String accountLabel(DeclaredAccount account) {
    return account.accountName().value() + " [" + account.accountCode().value() + "]";
  }

  static String displayLineType(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Asset";
      case LIABILITY -> "Liability";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expense";
    };
  }

  static String displayNormalBalance(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> lineCode;
      case CURRENT_PERIOD_RESULT -> "Calculated line";
    };
  }

  static String displayStatementLineKind(StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> "Account";
      case CURRENT_PERIOD_RESULT -> "Current period result";
    };
  }

  static String displayPostingOriginKind(PostingOriginKind postingOriginKind) {
    return java.util.Objects.requireNonNull(
        POSTING_ORIGIN_LABELS.get(postingOriginKind), "postingOriginKind label");
  }
}
