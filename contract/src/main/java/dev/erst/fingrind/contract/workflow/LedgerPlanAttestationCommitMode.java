package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/**
 * Required relationship between a successful plan disposition and its aggregate commitment field.
 */
public enum LedgerPlanAttestationCommitMode implements WireValue {
  /** The successful response must carry the exact aggregate attestation commitment. */
  REQUIRED(true),
  /** The successful response must carry the field explicitly as JSON null. */
  MUST_BE_NULL(false);

  private final boolean requiresAttestationCommit;

  LedgerPlanAttestationCommitMode(boolean requiresAttestationCommit) {
    this.requiresAttestationCommit = requiresAttestationCommit;
  }

  boolean requiresAttestationCommit() {
    return requiresAttestationCommit;
  }

  /** Returns the stable wire value for this commitment-field mode. */
  @Override
  public String wireValue() {
    return switch (this) {
      case REQUIRED -> "required";
      case MUST_BE_NULL -> "must-be-null";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerPlanAttestationCommitMode.class);
  }

  /** Parses one stable wire value. */
  public static LedgerPlanAttestationCommitMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerPlanAttestationCommitMode.class,
        wireValue,
        "Unsupported ledger plan attestation commit mode");
  }
}
