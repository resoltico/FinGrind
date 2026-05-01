package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable machine-readable provenance for the loaded SQLite runtime artifact. */
public enum SqliteRuntimeProvenance implements WireValue {
  BUNDLE_MANAGED("bundle-managed"),
  SOURCE_CHECKOUT_MANAGED("source-checkout-managed"),
  ENVIRONMENT_CONFIGURED("environment-configured");

  private final String wireValue;

  SqliteRuntimeProvenance(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns the supported wire values in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SqliteRuntimeProvenance.class);
  }

  /** Resolves one canonical runtime-provenance wire value. */
  public static SqliteRuntimeProvenance fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SqliteRuntimeProvenance.class, wireValue, "Unsupported SQLite runtime provenance");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
