package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
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

  /**
   * Returns one entry-semantics violation for an account whose declared type contradicts the
   * request.
   */
  public static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String entryLabel,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedAccountType, "expectedAccountType");
    Objects.requireNonNull(actualAccountType, "actualAccountType");
    return new PostingRejection.EntrySemanticsViolation(
        "account-type-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to be account type '%s', but the declared account type is '%s'."
            .formatted(
                requiredEntryLabel,
                field,
                accountCode.value(),
                expectedAccountType.wireValue(),
                actualAccountType.wireValue()));
  }

  /**
   * Returns one entry-semantics violation for an account whose declared financial-position
   * classification contradicts the entry.
   */
  public static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
      String entryLabel,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new PostingRejection.EntrySemanticsViolation(
        "financial-position-classification-mismatch",
        field,
        "Entry kind '%s' requires %s '%s' to use financialPositionLineClassification '%s', but the declared account uses '%s'."
            .formatted(
                requiredEntryLabel,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /**
   * Returns one entry-semantics violation for evidence whose source-document type is not admitted.
   */
  public static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String entryLabel, SourceDocumentType sourceDocumentType, List<String> acceptedTypes) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedTypeValues =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new PostingRejection.EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "Entry kind '%s' does not accept sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                requiredEntryLabel,
                sourceDocumentType.value(),
                String.join(", ", acceptedTypeValues)));
  }

  /** Returns one entry-semantics violation when two semantic roles collapse onto one account. */
  public static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String entryLabel, String firstField, String secondField, AccountCode accountCode) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    Objects.requireNonNull(firstField, "firstField");
    Objects.requireNonNull(secondField, "secondField");
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "distinct-role-accounts-required",
        null,
        "Entry kind '%s' requires %s and %s to reference distinct accounts, but both point to '%s'."
            .formatted(requiredEntryLabel, firstField, secondField, accountCode.value()));
  }

  /** Returns one entry-semantics violation for raw journals that net every account to zero. */
  public static PostingRejection.EntrySemanticsViolation economicNullJournal(String entryLabel) {
    String requiredEntryLabel = ContractDescriptorValidation.requireText(entryLabel, "entryLabel");
    return new PostingRejection.EntrySemanticsViolation(
        "economic-null-journal",
        "lines",
        "Entry kind '%s' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement."
            .formatted(requiredEntryLabel));
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
