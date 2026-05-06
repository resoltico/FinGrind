package dev.erst.fingrind.executor.workflow;

/** Internal workflow boundary phases for begin/check/commit/rollback failures. */
public enum BookWorkflowBoundaryPhase {
  BEGIN("begin"),
  INITIALIZATION_CHECK("initialization-check"),
  COMMIT("commit"),
  ROLLBACK("rollback");

  private final String wireValue;

  BookWorkflowBoundaryPhase(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable machine-facing phase wire value. */
  public String wireValue() {
    return wireValue;
  }
}
