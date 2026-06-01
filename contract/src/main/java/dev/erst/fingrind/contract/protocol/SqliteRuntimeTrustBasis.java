package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable machine-readable trust basis for the loaded SQLite runtime artifact. */
public enum SqliteRuntimeTrustBasis implements WireValue {
  BUNDLE_SIDECAR_CONSISTENCY("bundle-sidecar-consistency"),
  SOURCE_CHECKOUT_SIDECAR_CONSISTENCY("source-checkout-sidecar-consistency");

  private final String wireValue;

  SqliteRuntimeTrustBasis(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns the supported wire values in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SqliteRuntimeTrustBasis.class);
  }

  /** Resolves one canonical runtime-trust wire value. */
  public static SqliteRuntimeTrustBasis fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SqliteRuntimeTrustBasis.class, wireValue, "Unsupported SQLite runtime trust basis");
  }

  /** Maps one runtime provenance class to its public trust basis. */
  public static SqliteRuntimeTrustBasis fromProvenance(SqliteRuntimeProvenance provenance) {
    return switch (Objects.requireNonNull(provenance, "provenance")) {
      case BUNDLE_MANAGED -> BUNDLE_SIDECAR_CONSISTENCY;
      case SOURCE_CHECKOUT_MANAGED -> SOURCE_CHECKOUT_SIDECAR_CONSISTENCY;
    };
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
