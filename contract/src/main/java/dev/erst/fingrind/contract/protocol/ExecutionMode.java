package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;

/** Public output mode for one FinGrind operation. */
public enum ExecutionMode implements WireValue {
  /** Operation returns a status envelope with a payload. */
  JSON_ENVELOPE("json-envelope"),
  /** Operation returns one raw JSON document. */
  RAW_JSON("raw-json");

  private final String wireValue;

  ExecutionMode(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable machine-readable value for this execution mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }
}
