package dev.erst.fingrind.executor.bookkeeping.policy;

import java.util.Objects;

/** Policy-owned descriptor for one derived equity line published into statement outputs. */
public record DerivedEquityLine(String lineCode, String lineName) {
  public DerivedEquityLine {
    lineCode = Objects.requireNonNull(lineCode, "lineCode").strip();
    lineName = Objects.requireNonNull(lineName, "lineName").strip();
    if (lineCode.isEmpty()) {
      throw new IllegalArgumentException("lineCode must not be blank.");
    }
    if (lineName.isEmpty()) {
      throw new IllegalArgumentException("lineName must not be blank.");
    }
  }
}
