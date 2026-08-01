package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/**
 * Required relationship between a successful plan disposition and supplied attestation credentials.
 */
public enum LedgerPlanAttestationCredentialMode implements WireValue {
  /** A complete attestation credential tuple is required before the plan may execute. */
  REQUIRED,
  /** A complete attestation credential tuple is structurally prohibited for the plan. */
  PROHIBITED;

  /** Returns the stable wire value for this credential mode. */
  @Override
  public String wireValue() {
    return switch (this) {
      case REQUIRED -> "required";
      case PROHIBITED -> "prohibited";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerPlanAttestationCredentialMode.class);
  }

  /** Parses one stable wire value. */
  public static LedgerPlanAttestationCredentialMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerPlanAttestationCredentialMode.class,
        wireValue,
        "Unsupported ledger plan attestation credential mode");
  }
}
