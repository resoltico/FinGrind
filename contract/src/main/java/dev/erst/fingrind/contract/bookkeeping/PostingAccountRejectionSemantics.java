package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical owner for account-driven posting entry-semantics rejection details. */
final class PostingAccountRejectionSemantics {
  private PostingAccountRejectionSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    return accountTypeMismatch(
        "entryKind", selectorValue, field, accountCode, expectedAccountType, actualAccountType);
  }

  /**
   * Returns one entry-semantics violation for an account whose declared type contradicts the
   * request.
   */
  static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedAccountType, "expectedAccountType");
    Objects.requireNonNull(actualAccountType, "actualAccountType");
    return new PostingRejection.EntrySemanticsViolation(
        "account-type-mismatch",
        field,
        "%s '%s' requires %s '%s' to be account type '%s', but the declared account type is '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                field,
                accountCode.value(),
                expectedAccountType.wireValue(),
                actualAccountType.wireValue()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation cashFlowAssetClassificationMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      CashFlowAssetClassification expectedClassification,
      @Nullable CashFlowAssetClassification actualClassification) {
    return cashFlowAssetClassificationMismatch(
        "entryKind",
        selectorValue,
        field,
        accountCode,
        expectedClassification,
        actualClassification);
  }

  /**
   * Returns one entry-semantics violation for an asset whose declared cash-flow classification
   * contradicts the entry.
   */
  static PostingRejection.EntrySemanticsViolation cashFlowAssetClassificationMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      CashFlowAssetClassification expectedClassification,
      @Nullable CashFlowAssetClassification actualClassification) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new PostingRejection.EntrySemanticsViolation(
        "cash-flow-asset-classification-mismatch",
        field,
        "%s '%s' requires %s '%s' to use cashFlowAssetClassification '%s', but the declared account uses '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    return financialPositionClassificationMismatch(
        "entryKind",
        selectorValue,
        field,
        accountCode,
        expectedClassification,
        actualClassification);
  }

  /**
   * Returns one entry-semantics violation for an account whose declared financial-position
   * classification contradicts the entry.
   */
  static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new PostingRejection.EntrySemanticsViolation(
        "financial-position-classification-mismatch",
        field,
        "%s '%s' requires %s '%s' to use financialPositionLineClassification '%s', but the declared account uses '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorValue, String firstField, String secondField, AccountCode accountCode) {
    return distinctRoleAccountsRequired(
        "entryKind", selectorValue, firstField, secondField, accountCode);
  }

  /** Returns one entry-semantics violation when two semantic roles collapse onto one account. */
  static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorField,
      String selectorValue,
      String firstField,
      String secondField,
      AccountCode accountCode) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(firstField, "firstField");
    Objects.requireNonNull(secondField, "secondField");
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "distinct-role-accounts-required",
        null,
        "%s '%s' requires %s and %s to reference distinct accounts, but both point to '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                firstField,
                secondField,
                accountCode.value()));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation accountRoleMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountRole expectedRole,
      AccountRole actualRole) {
    return accountRoleMismatch(
        "entryKind", selectorValue, field, accountCode, expectedRole, actualRole);
  }

  /**
   * Returns one entry-semantics violation for an account whose resolved role contradicts the entry.
   */
  static PostingRejection.EntrySemanticsViolation accountRoleMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountRole expectedRole,
      AccountRole actualRole) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedRole, "expectedRole");
    Objects.requireNonNull(actualRole, "actualRole");
    return new PostingRejection.EntrySemanticsViolation(
        "account-role-mismatch",
        field,
        "%s '%s' requires %s '%s' to resolve to accountRole '%s', but the declared account resolves to '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                field,
                accountCode.value(),
                expectedRole.wireValue(),
                actualRole.wireValue()));
  }
}
