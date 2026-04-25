package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public bundle-target identifiers for downloadable FinGrind CLI artifacts. */
public enum PublicCliBundleTarget implements WireValue {
  MACOS_AARCH64("macos-aarch64"),
  MACOS_X86_64("macos-x86_64"),
  LINUX_X86_64("linux-x86_64"),
  LINUX_AARCH64("linux-aarch64"),
  WINDOWS_X86_64("windows-x86_64"),
  WINDOWS_AARCH64("windows-aarch64");

  private final String wireValue;

  PublicCliBundleTarget(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this public CLI bundle target. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public CLI bundle target in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PublicCliBundleTarget.class);
  }

  /** Parses one stable public CLI bundle target identifier. */
  public static PublicCliBundleTarget fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PublicCliBundleTarget.class, wireValue, "Unsupported public CLI bundle target");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
