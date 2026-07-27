package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.ReportCapabilityFacts;
import java.util.List;

/** Descriptor for the machine-readable executable bookkeeping kernel. */
public record BookkeepingKernelDescriptor(
    String scope,
    List<String> builtInStatements,
    List<ReportCapabilityFacts> reportCapabilities,
    String description)
    implements ResponseDescriptorType {
  /** Validates one bookkeeping-kernel descriptor payload. */
  public BookkeepingKernelDescriptor {
    scope = ContractDescriptorValidation.requireText(scope, "scope");
    builtInStatements =
        ContractDescriptorValidation.copyList(builtInStatements, "builtInStatements");
    reportCapabilities =
        ContractDescriptorValidation.copyList(reportCapabilities, "reportCapabilities");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
