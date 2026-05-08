package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** One typed quick-start workflow step in the public help contract. */
public sealed interface WorkflowStepDescriptor
    permits WorkflowStepDescriptor.Command,
        WorkflowStepDescriptor.Edit,
        WorkflowStepDescriptor.Note {
  /** Returns the canonical machine-readable kind for this workflow step. */
  WorkflowStepKind kind();

  /** Builds one command step. */
  static Command command(String text) {
    return new Command(text);
  }

  /** Builds one required file-write step with canonical contents. */
  static Edit edit(String path, String content) {
    return new Edit(path, content);
  }

  /** Builds one explanatory note step. */
  static Note note(String text) {
    return new Note(text);
  }

  /** One command-line step that the quick-start workflow asks the user to run. */
  record Command(String text) implements WorkflowStepDescriptor {
    public Command {
      text = ContractDescriptorValidation.requireText(text, "text");
    }

    @Override
    public WorkflowStepKind kind() {
      return WorkflowStepKind.COMMAND;
    }
  }

  /** One file-write step with the exact path and canonical content to write. */
  record Edit(String path, String content) implements WorkflowStepDescriptor {
    public Edit {
      path = ContractDescriptorValidation.requireText(path, "path");
      content = ContractDescriptorValidation.requireText(content, "content");
    }

    @Override
    public WorkflowStepKind kind() {
      return WorkflowStepKind.EDIT;
    }
  }

  /** One explanatory note in the quick-start workflow. */
  record Note(String text) implements WorkflowStepDescriptor {
    public Note {
      text = ContractDescriptorValidation.requireText(text, "text");
    }

    @Override
    public WorkflowStepKind kind() {
      return WorkflowStepKind.NOTE;
    }
  }
}
