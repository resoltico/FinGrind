package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Canonical owner for building posting entry-semantics rejection details from business facts. */
public final class PostingRejectionSemantics {
  private PostingRejectionSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
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
  public static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
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
  public static PostingRejection.EntrySemanticsViolation cashFlowAssetClassificationMismatch(
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
  public static PostingRejection.EntrySemanticsViolation cashFlowAssetClassificationMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      CashFlowAssetClassification expectedClassification,
      @Nullable CashFlowAssetClassification actualClassification) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
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
  public static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
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
  public static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
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
  public static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorValue, SourceDocumentType sourceDocumentType, List<String> acceptedTypes) {
    return sourceDocumentTypeNotAccepted(
        "entryKind", selectorValue, sourceDocumentType, acceptedTypes);
  }

  /**
   * Returns one entry-semantics violation for evidence whose source-document type is not admitted.
   */
  public static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorField,
      String selectorValue,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedTypeValues =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new PostingRejection.EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "%s '%s' does not accept evidence.sourceDocuments[].sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                sourceDocumentType.value(),
                String.join(", ", acceptedTypeValues)));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorValue, String firstField, String secondField, AccountCode accountCode) {
    return distinctRoleAccountsRequired(
        "entryKind", selectorValue, firstField, secondField, accountCode);
  }

  /** Returns one entry-semantics violation when two semantic roles collapse onto one account. */
  public static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorField,
      String selectorValue,
      String firstField,
      String secondField,
      AccountCode accountCode) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
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
  public static PostingRejection.EntrySemanticsViolation economicNullJournal(String selectorValue) {
    return economicNullJournal("entryKind", selectorValue);
  }

  /** Returns one entry-semantics violation for raw journals that net every account to zero. */
  public static PostingRejection.EntrySemanticsViolation economicNullJournal(
      String selectorField, String selectorValue) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    return new PostingRejection.EntrySemanticsViolation(
        "economic-null-journal",
        "lines",
        "%s '%s' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement."
            .formatted(requiredSelectorField, requiredSelectorValue));
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation cashBasisAccountRequired(
      String selectorValue, List<AccountCode> referencedAccountCodes) {
    return cashBasisAccountRequired("entryKind", selectorValue, referencedAccountCodes);
  }

  /**
   * Returns one entry-semantics violation for a direct journal that omits every declared
   * cash-and-cash-equivalent asset account.
   */
  public static PostingRejection.EntrySemanticsViolation cashBasisAccountRequired(
      String selectorField, String selectorValue, List<AccountCode> referencedAccountCodes) {
    String requiredSelectorField =
        ContractDescriptorValidation.requireText(selectorField, "selectorField");
    String requiredSelectorValue =
        ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
    List<AccountCode> requiredAccountCodes =
        List.copyOf(Objects.requireNonNull(referencedAccountCodes, "referencedAccountCodes"));
    if (requiredAccountCodes.isEmpty()) {
      throw new IllegalArgumentException("referencedAccountCodes must contain at least one item.");
    }
    requiredAccountCodes.forEach(accountCode -> Objects.requireNonNull(accountCode, "accountCode"));
    return new PostingRejection.EntrySemanticsViolation(
        "cash-basis-account-required",
        "lines[].accountCode",
        "%s '%s' requires at least one lines[].accountCode to reference a declared cash-and-cash-equivalent asset account, but the request references only %s."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                quotedAccountCodes(requiredAccountCodes)));
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

  private static String quotedAccountCodes(List<AccountCode> accountCodes) {
    return accountCodes.stream()
        .map(AccountCode::value)
        .map("'%s'"::formatted)
        .collect(java.util.stream.Collectors.joining(", "));
  }
}
