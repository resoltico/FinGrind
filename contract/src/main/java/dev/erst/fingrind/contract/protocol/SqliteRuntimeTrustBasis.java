package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable machine-readable trust basis for the loaded SQLite runtime artifact. */
public enum SqliteRuntimeTrustBasis implements WireValue {
  PUBLISHER_AUTHENTICATED("publisher-authenticated"),
  SOURCE_VERIFIED_LOCAL_BUILD("source-verified-local-build"),
  UNSAFE_LOCAL_OVERRIDE("unsafe-local-override");

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
      case BUNDLE_MANAGED -> PUBLISHER_AUTHENTICATED;
      case SOURCE_CHECKOUT_MANAGED -> SOURCE_VERIFIED_LOCAL_BUILD;
      case ENVIRONMENT_CONFIGURED -> UNSAFE_LOCAL_OVERRIDE;
    };
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
