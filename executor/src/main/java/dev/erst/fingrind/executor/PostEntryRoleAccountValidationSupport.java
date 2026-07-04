package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.AccountExpectation;
import dev.erst.fingrind.executor.PostEntryRoleAccountExpectations.DistinctAccountPair;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAccountSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Package-private validators for typed entry account-role semantics. */
final class PostEntryRoleAccountValidationSupport {
  private PostEntryRoleAccountValidationSupport() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      DistinctAccountPair distinctPair,
      AccountExpectation... expectations) {
    requireDistinctRoleAccounts(violations, selectorField, selectorValue, distinctPair);
    for (AccountExpectation expectation : expectations) {
      validateExpectation(violations, accounts, selectorField, selectorValue, expectation);
    }
  }

  static void validateOptional(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      @Nullable AccountExpectation expectation) {
    if (expectation == null) {
      return;
    }
    validateExpectation(violations, accounts, selectorField, selectorValue, expectation);
  }

  private static void validateExpectation(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      AccountExpectation expectation) {
    if (expectation.expectedAccountType() != null) {
      requireAccountType(
          violations,
          selectorField,
          selectorValue,
          accounts,
          expectation.accountCode(),
          expectation.field(),
          expectation.expectedAccountType());
    }
    if (expectation.expectedFinancialPositionClassification() != null) {
      requireFinancialPositionClassification(
          violations,
          selectorField,
          selectorValue,
          accounts,
          expectation.accountCode(),
          expectation.field(),
          expectation.expectedFinancialPositionClassification());
    }
    if (expectation.expectedCashFlowAssetClassification() != null) {
      requireCashFlowAssetClassification(
          violations,
          selectorField,
          selectorValue,
          accounts,
          expectation.accountCode(),
          expectation.field(),
          expectation.expectedCashFlowAssetClassification());
    }
    if (expectation.expectedAccountRole() != null) {
      requireAccountRole(
          violations,
          selectorField,
          selectorValue,
          accounts,
          expectation.accountCode(),
          expectation.field(),
          expectation.expectedAccountRole());
    }
  }

  private static void requireDistinctRoleAccounts(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      DistinctAccountPair distinctPair) {
    if (!distinctPair.firstAccountCode().equals(distinctPair.secondAccountCode())) {
      return;
    }
    violations.add(
        BookkeepingAccountSemanticsViolations.distinctRoleAccountsRequired(
            selectorField,
            selectorValue,
            distinctPair.firstField(),
            distinctPair.secondField(),
            distinctPair.firstAccountCode()));
  }

  private static void requireAccountType(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      AccountType expectedAccountType) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null || account.accountType() == expectedAccountType) {
      return;
    }
    violations.add(
        BookkeepingAccountSemanticsViolations.accountTypeMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            expectedAccountType,
            account.accountType()));
  }

  private static void requireFinancialPositionClassification(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      FinancialPositionLineClassification expectedClassification) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null) {
      return;
    }
    FinancialPositionLineClassification actualClassification =
        account.accountTaxonomy().financialPositionLineClassification().orElse(null);
    if (actualClassification == expectedClassification) {
      return;
    }
    violations.add(
        BookkeepingAccountSemanticsViolations.financialPositionClassificationMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  private static void requireCashFlowAssetClassification(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      CashFlowAssetClassification expectedClassification) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null) {
      return;
    }
    CashFlowAssetClassification actualClassification =
        account.accountTaxonomy().cashFlowAssetClassification().orElse(null);
    if (actualClassification == expectedClassification) {
      return;
    }
    violations.add(
        BookkeepingAccountSemanticsViolations.cashFlowAssetClassificationMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  private static void requireAccountRole(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      AccountRole expectedRole) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null) {
      return;
    }
    AccountRole actualRole = AccountRole.from(account.accountType(), account.accountTaxonomy());
    if (actualRole == expectedRole) {
      return;
    }
    violations.add(
        BookkeepingAccountSemanticsViolations.accountRoleMismatch(
            selectorField, selectorValue, field, accountCode, expectedRole, actualRole));
  }
}
