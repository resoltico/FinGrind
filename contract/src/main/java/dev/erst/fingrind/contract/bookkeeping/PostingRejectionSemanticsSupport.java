package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Shared selector validation for posting entry-semantics rejection builders. */
final class PostingRejectionSemanticsSupport {
  private PostingRejectionSemanticsSupport() {}

  static String requireSelectorField(String selectorField) {
    return ContractDescriptorValidation.requireText(selectorField, "selectorField");
  }

  static String requireSelectorValue(String selectorValue) {
    return ContractDescriptorValidation.requireText(selectorValue, "selectorValue");
  }
}
