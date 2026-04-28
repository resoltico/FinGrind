package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Objects;

/** One typed quick-start workflow step in the public help contract. */
public record WorkflowStepDescriptor(WorkflowStepKind kind, String text) {
  public WorkflowStepDescriptor {
    Objects.requireNonNull(kind, "kind");
    text = ContractDescriptorValidation.requireText(text, "text");
  }

  /** Builds one command step. */
  public static WorkflowStepDescriptor command(String text) {
    return new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, text);
  }

  /** Builds one required file-edit or replacement step. */
  public static WorkflowStepDescriptor edit(String text) {
    return new WorkflowStepDescriptor(WorkflowStepKind.EDIT, text);
  }

  /** Builds one explanatory note step. */
  public static WorkflowStepDescriptor note(String text) {
    return new WorkflowStepDescriptor(WorkflowStepKind.NOTE, text);
  }
}
