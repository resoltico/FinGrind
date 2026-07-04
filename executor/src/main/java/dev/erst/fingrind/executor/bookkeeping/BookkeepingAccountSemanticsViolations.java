package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Executor-local account-semantics violations derived from canonical contract rejections. */
public final class BookkeepingAccountSemanticsViolations {
  private BookkeepingAccountSemanticsViolations() {}

  /** Creates one account-type mismatch violation for one explicit selector field and value. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.accountTypeMismatch(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            field,
            accountCode,
            expectedAccountType,
            actualAccountType));
  }

  /** Creates one cash-flow asset classification mismatch for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      cashFlowAssetClassificationMismatch(
          String selectorField,
          String selectorValue,
          String field,
          AccountCode accountCode,
          CashFlowAssetClassification expectedClassification,
          @Nullable CashFlowAssetClassification actualClassification) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.cashFlowAssetClassificationMismatch(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  /** Creates one financial-position classification mismatch for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      financialPositionClassificationMismatch(
          String selectorField,
          String selectorValue,
          String field,
          AccountCode accountCode,
          FinancialPositionLineClassification expectedClassification,
          @Nullable FinancialPositionLineClassification actualClassification) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.financialPositionClassificationMismatch(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  /** Creates one distinct-role-accounts violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorField,
      String selectorValue,
      String firstField,
      String secondField,
      AccountCode accountCode) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.distinctRoleAccountsRequired(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            firstField,
            secondField,
            accountCode));
  }

  /** Creates one account-role mismatch violation for a semantic role field. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation accountRoleMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountRole expectedRole,
      AccountRole actualRole) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.accountRoleMismatch(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            field,
            accountCode,
            expectedRole,
            actualRole));
  }

  /** Returns one insertion-ordered set of referenced accounts without rejecting duplicates. */
  public static Set<AccountCode> referencedAccountSet(AccountCode... accountCodes) {
    Objects.requireNonNull(accountCodes, "accountCodes");
    Set<AccountCode> referencedAccounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      referencedAccounts.add(Objects.requireNonNull(accountCode, "accountCode"));
    }
    return referencedAccounts;
  }
}
