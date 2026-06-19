package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Canonical owner for entry-semantics violation metadata, ordering, and publication. */
enum EntrySemanticsViolationOwner {
  ECONOMIC_NULL_JOURNAL(
      "economic-null-journal",
      "journal-lines",
      "The supplied raw journal reduces every referenced account to zero after debit-credit netting.",
      "Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting."),
  DISTINCT_ROLE_ACCOUNTS_REQUIRED(
      "distinct-role-accounts-required",
      "account-role-assignment",
      "Two semantic role fields point to the same account even though the selected entry kind requires distinct accounts.",
      "Assign distinct accounts to the semantic role fields named in the violation."),
  ACCOUNT_TYPE_MISMATCH(
      "account-type-mismatch",
      "account-type",
      "One referenced account uses a declared account type that the selected entry kind does not accept.",
      "Use accounts whose declared account type matches the violated field requirement."),
  FINANCIAL_POSITION_CLASSIFICATION_MISMATCH(
      "financial-position-classification-mismatch",
      "financialPositionClassification",
      "One referenced account uses a declared financialPositionLineClassification that the selected entry kind does not accept.",
      "Use accounts whose declared financialPositionLineClassification matches the violated field requirement."),
  SOURCE_DOCUMENT_TYPE_NOT_ACCEPTED(
      "source-document-type-not-accepted",
      "source-document-type",
      "One evidence source document uses a sourceDocumentType that the selected entry kind does not accept.",
      "Use an accepted source document type for the selected entry kind's evidence profile.");

  private static final Map<String, EntrySemanticsViolationOwner> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  EntrySemanticsViolationOwner::code, Function.identity()));

  private static final Map<String, Integer> ORDER_BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  EntrySemanticsViolationOwner::code,
                  owner -> Arrays.asList(values()).indexOf(owner)));

  private static final Comparator<PostingRejection.EntrySemanticsViolation> CANONICAL_ORDER =
      Comparator.comparingInt(violation -> ORDER_BY_CODE.get(require(violation.code()).code()));

  private static final List<ContractResponse.FieldDescriptor> DETAIL_FIELDS =
      List.of(
          detailField("code", "Stable entry-semantics violation code."),
          detailField("field", "Optional request-field path associated with this violation."),
          detailField("message", "Canonical plain-language explanation for this one violation."),
          detailField("category", "Stable repair category owned by this violation code."),
          detailField("repair", "Canonical action-first repair guidance for this one violation."));

  private final String code;
  private final String category;
  private final String description;
  private final String repair;

  EntrySemanticsViolationOwner(String code, String category, String description, String repair) {
    this.code = ContractDescriptorValidation.requireText(code, "code");
    this.category = ContractDescriptorValidation.requireText(category, "category");
    this.description = ContractDescriptorValidation.requireText(description, "description");
    this.repair = ContractDescriptorValidation.requireText(repair, "repair");
  }

  String code() {
    return code;
  }

  String category() {
    return category;
  }

  String repair() {
    return repair;
  }

  private ContractResponse.RejectionDescriptor descriptor() {
    return new ContractResponse.RejectionDescriptor(code, description, DETAIL_FIELDS, List.of());
  }

  static EntrySemanticsViolationOwner require(String code) {
    String requiredCode = ContractDescriptorValidation.requireText(code, "code");
    EntrySemanticsViolationOwner owner = BY_CODE.get(requiredCode);
    if (owner == null) {
      throw new IllegalArgumentException(
          "Unsupported entry semantics violation code: '%s'.".formatted(requiredCode));
    }
    return owner;
  }

  static void validateKnownMetadata(String code, String category, String repair) {
    @Nullable EntrySemanticsViolationOwner knownOwner = BY_CODE.get(code);
    if (knownOwner == null) {
      return;
    }
    if (!knownOwner.category.equals(category)) {
      throw new IllegalArgumentException(
          "Entry semantics violation category for code '%s' must be '%s'."
              .formatted(code, knownOwner.category));
    }
    if (!knownOwner.repair.equals(repair)) {
      throw new IllegalArgumentException(
          "Entry semantics violation repair for code '%s' must be '%s'."
              .formatted(code, knownOwner.repair));
    }
  }

  static List<PostingRejection.EntrySemanticsViolation> inCanonicalOrder(
      List<PostingRejection.EntrySemanticsViolation> violations) {
    return ContractDescriptorValidation.copyList(violations, "violations").stream()
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Arrays.stream(values()).map(EntrySemanticsViolationOwner::descriptor).toList();
  }

  static String envelopeMessage(List<PostingRejection.EntrySemanticsViolation> violations) {
    int issueCount = inCanonicalOrder(violations).size();
    return issueCount == 1
        ? "Posting rejected with 1 entry-semantics issue."
        : "Posting rejected with %d entry-semantics issues.".formatted(issueCount);
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(
        ContractDescriptorValidation.requireText(name, "name"),
        ContractDescriptorValidation.requireText(description, "description"));
  }
}
