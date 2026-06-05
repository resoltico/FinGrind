package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable workflow surfaces published by the help quick-start contract. */
public enum WorkflowSurface implements WireValue {
  BUNDLE_POSIX_SHELL("bundle-posix-shell"),
  SOURCE_CHECKOUT_POSIX_SHELL("source-checkout-posix-shell"),
  SOURCE_CHECKOUT_WINDOWS_POWERSHELL("source-checkout-windows-powershell"),
  DIRECT_JAVA_POSIX_SHELL("direct-java-posix-shell"),
  DIRECT_JAVA_WINDOWS_POWERSHELL("direct-java-windows-powershell"),
  CONTAINER_DOCKER("container-docker");

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
