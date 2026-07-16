package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.bookkeeping.BookTemplateSeedAccountFactory.postable;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Optional;

/** Built-in equity declarations shared by template doctrines. */
final class BookTemplateEquitySeedAccounts {
  private BookTemplateEquitySeedAccounts() {}

  static AccountDeclaration ownerCapitalAccount() {
    return postable(
        "owner-capital",
        "Owner Capital",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration ownerDrawsAccount() {
    return postable(
        "owner-draws",
        "Owner Draws",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.EQUITY_WITHDRAWAL),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration resultHoldingAccount() {
    return postable(
        "result-holding",
        "Result Holding",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
        Optional.empty(),
        Optional.empty());
  }

  static AccountDeclaration retainedAccumulatedAccount() {
    return postable(
        "retained-accumulated",
        "Retained Accumulated",
        AccountType.EQUITY,
        Optional.of(FinancialPositionLineClassification.RETAINED_ACCUMULATED),
        Optional.empty(),
        Optional.empty());
  }
}
