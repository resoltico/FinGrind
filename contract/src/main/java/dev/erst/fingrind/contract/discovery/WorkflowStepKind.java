package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.WireValue;

/** Canonical help-workflow step kinds for operator and agent-facing quick-start guidance. */
public enum WorkflowStepKind implements WireValue {
  COMMAND("command"),
  EDIT("edit"),
  NOTE("note");

  private final String wireValue;

  WorkflowStepKind(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }
}
