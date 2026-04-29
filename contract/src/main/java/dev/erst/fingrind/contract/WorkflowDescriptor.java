package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;

/** One platform-specific quick-start workflow sequence published through help discovery. */
public record WorkflowDescriptor(WorkflowSurface surface, List<WorkflowStepDescriptor> steps) {
  public WorkflowDescriptor {
    Objects.requireNonNull(surface, "surface");
    steps = ContractDescriptorValidation.copyList(steps, "steps");
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Workflow steps must not be empty.");
    }
  }
}
