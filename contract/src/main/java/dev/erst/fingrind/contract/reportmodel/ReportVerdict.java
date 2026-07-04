package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** One labeled summary fact projected with a report model. */
public record ReportVerdict(String label, String value) {
  /** Validates one report verdict row. */
  public ReportVerdict {
    label = ContractDescriptorValidation.requireText(label, "label");
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
