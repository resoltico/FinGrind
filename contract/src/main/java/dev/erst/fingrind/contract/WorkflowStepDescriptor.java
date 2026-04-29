package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One typed quick-start workflow step in the public help contract. */
public record WorkflowStepDescriptor(
    WorkflowStepKind kind, @Nullable String text, @Nullable String path, @Nullable String content) {
  public WorkflowStepDescriptor {
    Objects.requireNonNull(kind, "kind");
    text = ContractDescriptorValidation.requireOptionalText(text, "text");
    path = ContractDescriptorValidation.requireOptionalText(path, "path");
    content = ContractDescriptorValidation.requireOptionalText(content, "content");
    if (kind == WorkflowStepKind.COMMAND) {
      requireCommand(text, path, content);
    } else if (kind == WorkflowStepKind.EDIT) {
      requireEdit(text, path, content);
    } else {
      requireNote(text, path, content);
    }
  }

  /** Builds one command step. */
  public static WorkflowStepDescriptor command(String text) {
    return new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, text, null, null);
  }

  /** Builds one required file-write step with canonical contents. */
  public static WorkflowStepDescriptor edit(String path, String content) {
    return new WorkflowStepDescriptor(WorkflowStepKind.EDIT, null, path, content);
  }

  /** Builds one explanatory note step. */
  public static WorkflowStepDescriptor note(String text) {
    return new WorkflowStepDescriptor(WorkflowStepKind.NOTE, text, null, null);
  }

  private static void requireCommand(
      @Nullable String text, @Nullable String path, @Nullable String content) {
    Objects.requireNonNull(text, "Command workflow steps require text.");
    requireAbsent(path, "Command workflow steps must not declare file-edit payload.");
    requireAbsent(content, "Command workflow steps must not declare file-edit payload.");
  }

  private static void requireEdit(
      @Nullable String text, @Nullable String path, @Nullable String content) {
    requireAbsent(text, "Edit workflow steps must not declare free-form text.");
    Objects.requireNonNull(path, "Edit workflow steps require path and content.");
    Objects.requireNonNull(content, "Edit workflow steps require path and content.");
  }

  private static void requireNote(
      @Nullable String text, @Nullable String path, @Nullable String content) {
    Objects.requireNonNull(text, "Note workflow steps require text.");
    requireAbsent(path, "Note workflow steps must not declare file-edit payload.");
    requireAbsent(content, "Note workflow steps must not declare file-edit payload.");
  }

  private static void requireAbsent(@Nullable String value, String message) {
    if (value != null) {
      throw new IllegalArgumentException(message);
    }
  }
}
