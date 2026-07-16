package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;
import static dev.erst.fingrind.executor.PostEntryFinancingAccountExpectations.currentLiability;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.expense;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Role-account admission for the Latvian monthly-payroll typed write vocabulary. */
final class PostEntryLatvianPayrollRoleAccountSemantics {
  private PostEntryLatvianPayrollRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      LatvianPayrollBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          validateMonthlyPayroll(violations, accounts, payroll, selectorField, selectorValue);
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          validateSettlement(
              violations, accounts, settlement.cashAccountCode(), selectorField, selectorValue);
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          validateSettlement(
              violations, accounts, settlement.cashAccountCode(), selectorField, selectorValue);
    }
  }

  private static void validateSettlement(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode cashAccountCode,
      String selectorField,
      String selectorValue) {
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        List.of(),
        cash(cashAccountCode, "cashAccountCode"));
  }

  private static void validateMonthlyPayroll(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll,
      String selectorField,
      String selectorValue) {
    PostEntryAccountExpectation wageExpense =
        expense(payroll.wageExpenseAccountCode(), "wageExpenseAccountCode");
    PostEntryAccountExpectation employerSocialExpense =
        expense(
            payroll.employerSocialContributionExpenseAccountCode(),
            "employerSocialContributionExpenseAccountCode");
    PostEntryAccountExpectation netWagesPayable =
        currentLiability(payroll.netWagesPayableAccountCode(), "netWagesPayableAccountCode");
    PostEntryAccountExpectation employeeSocialPayable =
        currentLiability(
            payroll.employeeSocialContributionPayableAccountCode(),
            "employeeSocialContributionPayableAccountCode");
    PostEntryAccountExpectation employerSocialPayable =
        currentLiability(
            payroll.employerSocialContributionPayableAccountCode(),
            "employerSocialContributionPayableAccountCode");
    PostEntryAccountExpectation personalIncomeTaxPayable =
        currentLiability(
            payroll.personalIncomeTaxPayableAccountCode(), "personalIncomeTaxPayableAccountCode");
    List<PostEntryAccountExpectation> expectations =
        List.of(
            wageExpense,
            employerSocialExpense,
            netWagesPayable,
            employeeSocialPayable,
            employerSocialPayable,
            personalIncomeTaxPayable);
    List<PostEntryDistinctAccountPair> distinctPairs = new java.util.ArrayList<>();
    for (int first = 0; first < expectations.size(); first++) {
      for (int second = first + 1; second < expectations.size(); second++) {
        distinctPairs.add(distinct(expectations.get(first), expectations.get(second)));
      }
    }
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        distinctPairs,
        expectations.toArray(PostEntryAccountExpectation[]::new));
  }
}
