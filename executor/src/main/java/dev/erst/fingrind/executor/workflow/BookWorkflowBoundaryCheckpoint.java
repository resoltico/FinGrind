package dev.erst.fingrind.executor.workflow;

/** Internal workflow boundary checkpoints for begin/check/commit/rollback failures. */
public enum BookWorkflowBoundaryCheckpoint {
  BEGIN("begin"),
  INITIALIZATION_CHECK("initialization-check"),
  COMMIT("commit"),
  ROLLBACK("rollback");

  private final String wireValue;

  BookWorkflowBoundaryCheckpoint(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable machine-facing checkpoint wire value. */
  public String wireValue() {
    return wireValue;
  }
}
