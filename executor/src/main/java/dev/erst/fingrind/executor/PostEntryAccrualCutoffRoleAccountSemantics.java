package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;
import static dev.erst.fingrind.executor.PostEntryAccrualCutoffAccountExpectations.accruedExpense;
import static dev.erst.fingrind.executor.PostEntryAccrualCutoffAccountExpectations.deferredRevenue;
import static dev.erst.fingrind.executor.PostEntryAccrualCutoffAccountExpectations.prepaymentAsset;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.expense;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.revenue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Role-account admission for the accrual cut-off typed write vocabulary. */
final class PostEntryAccrualCutoffRoleAccountSemantics {
  private PostEntryAccrualCutoffRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      AccrualCutoffBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          validateTriplet(
              violations,
              accounts,
              selectorField,
              selectorValue,
              prepaymentAsset(
                  prepayment.prepaymentAssetAccountCode(), "prepaymentAssetAccountCode"),
              expense(prepayment.expenseAccountCode(), "expenseAccountCode"),
              cash(prepayment.cashAccountCode(), "cashAccountCode"));
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          validateTriplet(
              violations,
              accounts,
              selectorField,
              selectorValue,
              cash(deferredRevenue.cashAccountCode(), "cashAccountCode"),
              deferredRevenue(
                  deferredRevenue.deferredRevenueAccountCode(), "deferredRevenueAccountCode"),
              revenue(deferredRevenue.revenueAccountCode(), "revenueAccountCode"));
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          validatePair(
              violations,
              accounts,
              selectorField,
              selectorValue,
              expense(accruedExpense.expenseAccountCode(), "expenseAccountCode"),
              accruedExpense(
                  accruedExpense.accruedExpenseLiabilityAccountCode(),
                  "accruedExpenseLiabilityAccountCode"));
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition -> {
        if (recognition.resolvedApplication() != null) {
          validateResolvedRecognition(
              violations,
              accounts,
              selectorField,
              selectorValue,
              recognition.resolvedApplication());
        }
      }
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement -> {
        if (settlement.resolvedApplication() == null) {
          return;
        }
        ResolvedAccrualCutoffApplication resolvedApplication = settlement.resolvedApplication();
        if (resolvedApplication.accrualCutoffKind() != AccrualCutoffKind.ACCRUED_EXPENSE) {
          throw new IllegalStateException(
              "Accrued-expense settlement requires an accrued-expense settlement resolution.");
        }
        validatePair(
            violations,
            accounts,
            selectorField,
            selectorValue,
            accruedExpense(
                resolvedApplication.debitAccountCode(), "accruedExpenseLiabilityAccountCode"),
            cash(resolvedApplication.creditAccountCode(), "cashAccountCode"));
      }
    }
  }

  private static void validateResolvedRecognition(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      ResolvedAccrualCutoffApplication resolvedApplication) {
    if (resolvedApplication.accrualCutoffKind() == AccrualCutoffKind.PREPAYMENT) {
      validatePair(
          violations,
          accounts,
          selectorField,
          selectorValue,
          expense(resolvedApplication.debitAccountCode(), "expenseAccountCode"),
          prepaymentAsset(resolvedApplication.creditAccountCode(), "prepaymentAssetAccountCode"));
      return;
    }
    if (resolvedApplication.accrualCutoffKind() == AccrualCutoffKind.DEFERRED_REVENUE) {
      validatePair(
          violations,
          accounts,
          selectorField,
          selectorValue,
          deferredRevenue(resolvedApplication.debitAccountCode(), "deferredRevenueAccountCode"),
          revenue(resolvedApplication.creditAccountCode(), "revenueAccountCode"));
      return;
    }
    throw new IllegalStateException(
        "Accrued-expense cut-offs cannot use recognition applications.");
  }

  private static void validateTriplet(
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

  private static void validatePair(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      PostEntryAccountExpectation first,
      PostEntryAccountExpectation second) {
    PostEntryRoleAccountValidationSupport.validate(
        violations, accounts, selectorField, selectorValue, distinct(first, second), first, second);
  }
}
