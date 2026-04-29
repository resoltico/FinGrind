package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable workflow surfaces published by the help quick-start contract. */
public enum WorkflowSurface implements WireValue {
  BUNDLE_POSIX_SHELL("bundle-posix-shell"),
  BUNDLE_WINDOWS_POWERSHELL("bundle-windows-powershell");

  private final String wireValue;

  WorkflowSurface(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public workflow-surface wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(WorkflowSurface.class);
  }

  /** Parses one stable public workflow-surface wire value. */
  public static WorkflowSurface fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        WorkflowSurface.class, wireValue, "Unsupported workflow surface");
  }
}
