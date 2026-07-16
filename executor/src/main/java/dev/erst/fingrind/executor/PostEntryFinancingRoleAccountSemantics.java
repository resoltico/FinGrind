package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;
import static dev.erst.fingrind.executor.PostEntryFinancingAccountExpectations.currentLiability;
import static dev.erst.fingrind.executor.PostEntryFinancingAccountExpectations.liability;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.expense;

import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Role-account admission for the financing typed write vocabulary. */
final class PostEntryFinancingRoleAccountSemantics {
  private PostEntryFinancingRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      FinancingBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              cash(borrowing.cashAccountCode(), "cashAccountCode"),
              liability(borrowing.principalLiabilityAccountCode(), "principalLiabilityAccountCode"),
              currentLiability(
                  borrowing.interestPayableAccountCode(), "interestPayableAccountCode"));
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment -> {
        if (repayment.resolvedApplication() != null) {
          validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              cash(repayment.cashAccountCode(), "cashAccountCode"),
              liability(
                  repayment.resolvedApplication().principalLiabilityAccountCode(),
                  "principalLiabilityAccountCode"));
        }
      }
      case FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual -> {
        if (interestAccrual.resolvedApplication() != null) {
          validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              expense(interestAccrual.interestExpenseAccountCode(), "interestExpenseAccountCode"),
              currentLiability(
                  interestAccrual.resolvedApplication().interestPayableAccountCode(),
                  "interestPayableAccountCode"));
        }
      }
      case FinancingBookkeepingEntryVariants.InterestPayment interestPayment -> {
        if (interestPayment.resolvedApplication() != null) {
          validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              currentLiability(
                  interestPayment.resolvedApplication().interestPayableAccountCode(),
                  "interestPayableAccountCode"),
              cash(interestPayment.cashAccountCode(), "cashAccountCode"));
        }
      }
    }
  }

  private static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      PostEntryAccountExpectation first,
      PostEntryAccountExpectation second) {
    PostEntryRoleAccountSemantics.validatePair(
        violations, accounts, selectorField, selectorValue, first, second);
  }

  private static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      PostEntryAccountExpectation first,
      PostEntryAccountExpectation second,
      PostEntryAccountExpectation third) {
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        List.of(distinct(first, second), distinct(first, third), distinct(second, third)),
        first,
        second,
        third);
  }
}
