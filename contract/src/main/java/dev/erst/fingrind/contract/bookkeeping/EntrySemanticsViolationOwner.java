package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Canonical owner for entry-semantics violation metadata, ordering, and publication. */
final class EntrySemanticsViolationOwner {
  private static final List<EntrySemanticsViolationDefinition> DEFINITIONS =
      Stream.of(
              EntrySemanticsCoreViolationDefinitions.definitions(),
              EntrySemanticsInventoryViolationDefinitions.definitions(),
              EntrySemanticsAccrualCutoffViolationDefinitions.definitions(),
              EntrySemanticsLatvianPayrollViolationDefinitions.definitions(),
              EntrySemanticsFixedAssetViolationDefinitions.definitions(),
              EntrySemanticsFinancingViolationDefinitions.definitions(),
              EntrySemanticsRealizedForeignExchangeViolationDefinitions.definitions())
          .flatMap(List::stream)
          .toList();

  private static final Map<String, EntrySemanticsViolationDefinition> BY_CODE =
      DEFINITIONS.stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  EntrySemanticsViolationDefinition::code, Function.identity()));

  private static final Map<String, Integer> ORDER_BY_CODE = canonicalOrderByCode();

  private static final Comparator<PostingRejection.EntrySemanticsViolation> CANONICAL_ORDER =
      Comparator.comparingInt(violation -> ORDER_BY_CODE.get(require(violation.code()).code()));

  private static final List<ContractResponse.FieldDescriptor> DETAIL_FIELDS =
      List.of(
          detailField("code", "Stable entry-semantics violation code."),
          detailField("field", "Optional request-field path associated with this violation."),
          detailField("message", "Canonical plain-language explanation for this one violation."),
          detailField("category", "Stable repair category owned by this violation code."),
          detailField("repair", "Canonical action-first repair guidance for this one violation."));

  private EntrySemanticsViolationOwner() {}

  static EntrySemanticsViolationDefinition require(String code) {
    String requiredCode = ContractDescriptorValidation.requireText(code, "code");
    EntrySemanticsViolationDefinition owner = BY_CODE.get(requiredCode);
    if (owner == null) {
      throw new IllegalArgumentException(
          "Unsupported entry semantics violation code: '%s'.".formatted(requiredCode));
    }
    return owner;
  }

  static void validateKnownMetadata(String code, String category, String repair) {
    @Nullable EntrySemanticsViolationDefinition knownOwner = BY_CODE.get(code);
    if (knownOwner == null) {
      return;
    }
    if (!knownOwner.category().equals(category)) {
      throw new IllegalArgumentException(
          "Entry semantics violation category for code '%s' must be '%s'."
              .formatted(code, knownOwner.category()));
    }
    if (!knownOwner.repair().equals(repair)) {
      throw new IllegalArgumentException(
          "Entry semantics violation repair for code '%s' must be '%s'."
              .formatted(code, knownOwner.repair()));
    }
  }

  static List<PostingRejection.EntrySemanticsViolation> inCanonicalOrder(
      List<PostingRejection.EntrySemanticsViolation> violations) {
    return ContractDescriptorValidation.copyList(violations, "violations").stream()
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return DEFINITIONS.stream().map(definition -> definition.descriptor(DETAIL_FIELDS)).toList();
  }

  static String envelopeMessage(List<PostingRejection.EntrySemanticsViolation> violations) {
    int issueCount = inCanonicalOrder(violations).size();
    return issueCount == 1
        ? "Posting rejected with 1 entry-semantics issue."
        : "Posting rejected with %d entry-semantics issues.".formatted(issueCount);
  }

  private static Map<String, Integer> canonicalOrderByCode() {
    return java.util.stream.IntStream.range(0, DEFINITIONS.size())
        .boxed()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                index -> DEFINITIONS.get(index).code(), Function.identity()));
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(
        ContractDescriptorValidation.requireText(name, "name"),
        ContractDescriptorValidation.requireText(description, "description"));
  }
}
