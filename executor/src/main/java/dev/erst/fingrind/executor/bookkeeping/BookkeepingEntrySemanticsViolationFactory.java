package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
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

/** Factory owner for local entry-semantics rejection details. */
public final class BookkeepingEntrySemanticsViolationFactory {
  private BookkeepingEntrySemanticsViolationFactory() {}

  /** Creates one account-type mismatch violation for one explicit selector field and value. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorField,
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedAccountType, "expectedAccountType");
    Objects.requireNonNull(actualAccountType, "actualAccountType");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "account-type-mismatch",
        field,
        "%s '%s' requires %s '%s' to be account type '%s', but the declared account type is '%s'."
            .formatted(
                selectorField,
                selectorValue,
                field,
                accountCode.value(),
                expectedAccountType.wireValue(),
                actualAccountType.wireValue()));
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
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "cash-flow-asset-classification-mismatch",
        field,
        "%s '%s' requires %s '%s' to use cashFlowAssetClassification '%s', but the declared account uses '%s'."
            .formatted(
                selectorField,
                selectorValue,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
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
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(expectedClassification, "expectedClassification");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "financial-position-classification-mismatch",
        field,
        "%s '%s' requires %s '%s' to use financialPositionLineClassification '%s', but the declared account uses '%s'."
            .formatted(
                selectorField,
                selectorValue,
                field,
                accountCode.value(),
                expectedClassification.wireValue(),
                actualClassification == null ? "<absent>" : actualClassification.wireValue()));
  }

  /** Creates one source-document-type violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorField,
      String selectorValue,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedDocumentTypes =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "%s '%s' does not accept evidence.sourceDocuments[].sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                selectorField,
                selectorValue,
                sourceDocumentType.value(),
                String.join(", ", acceptedDocumentTypes)));
  }

  /** Creates one unknown-tax-registration violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation unknownTaxRegistration(
      String selectorField, String selectorValue, TaxRegistrationId taxRegistrationId) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "unknown-tax-registration",
        "tax.taxRegistrationId",
        "%s '%s' references tax.taxRegistrationId '%s', but that registration is not declared in this book."
            .formatted(selectorField, selectorValue, taxRegistrationId.value()));
  }

  /** Creates one unknown-tax-code violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation unknownTaxCode(
      String selectorField,
      String selectorValue,
      TaxRegistrationId taxRegistrationId,
      TaxCode taxCode) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    Objects.requireNonNull(taxCode, "taxCode");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "unknown-tax-code",
        "tax.taxCode",
        "%s '%s' references tax.taxCode '%s', but registration '%s' does not declare that code."
            .formatted(selectorField, selectorValue, taxCode.value(), taxRegistrationId.value()));
  }

  /** Creates one tax-application-kind mismatch violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation taxApplicationKindMismatch(
      String selectorField,
      String selectorValue,
      TaxCode taxCode,
      TaxApplicationKind expectedApplicationKind,
      TaxApplicationKind actualApplicationKind) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(expectedApplicationKind, "expectedApplicationKind");
    Objects.requireNonNull(actualApplicationKind, "actualApplicationKind");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "tax-application-kind-mismatch",
        "tax.taxCode",
        "%s '%s' requires tax.taxCode '%s' to resolve to applicationKind '%s', but the declared applicationKind is '%s'."
            .formatted(
                selectorField,
                selectorValue,
                taxCode.value(),
                expectedApplicationKind.wireValue(),
                actualApplicationKind.wireValue()));
  }

  /** Creates one distinct-role-accounts violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorField,
      String selectorValue,
      String firstField,
      String secondField,
      AccountCode accountCode) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    Objects.requireNonNull(firstField, "firstField");
    Objects.requireNonNull(secondField, "secondField");
    Objects.requireNonNull(accountCode, "accountCode");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "distinct-role-accounts-required",
        null,
        "%s '%s' requires %s and %s to reference distinct accounts, but both point to '%s'."
            .formatted(selectorField, selectorValue, firstField, secondField, accountCode.value()));
  }

  /** Creates one economic-null-journal violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation economicNullJournal(
      String selectorField, String selectorValue) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "economic-null-journal",
        "lines",
        "%s '%s' uses journal lines whose debit-credit netting reduces every referenced account to zero, so the journal would record no durable account movement."
            .formatted(selectorField, selectorValue));
  }

  /** Creates one cash-basis account-required violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation cashBasisAccountRequired(
      String selectorField, String selectorValue, List<AccountCode> referencedAccountCodes) {
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    List<AccountCode> requiredAccountCodes =
        List.copyOf(Objects.requireNonNull(referencedAccountCodes, "referencedAccountCodes"));
    if (requiredAccountCodes.isEmpty()) {
      throw new IllegalArgumentException("referencedAccountCodes must contain at least one item.");
    }
    requiredAccountCodes.forEach(accountCode -> Objects.requireNonNull(accountCode, "accountCode"));
    return new BookkeepingPostingRejection.EntrySemanticsViolation(
        "cash-basis-account-required",
        "lines[].accountCode",
        "%s '%s' requires at least one lines[].accountCode to reference a declared cash-and-cash-equivalent asset account, but the request references only %s."
            .formatted(
                selectorField,
                selectorValue,
                requiredAccountCodes.stream()
                    .map(AccountCode::value)
                    .map("'%s'"::formatted)
                    .collect(java.util.stream.Collectors.joining(", "))));
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
