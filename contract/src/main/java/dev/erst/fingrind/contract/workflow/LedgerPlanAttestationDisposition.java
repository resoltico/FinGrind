package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/**
 * Describes why a successful ledger-plan execution did or did not append its aggregate attestation
 * operation.
 */
public enum LedgerPlanAttestationDisposition implements WireValue {
  /** The mutation-capable execution committed durable children and its aggregate operation. */
  APPENDED(LedgerPlanAttestationCommitMode.REQUIRED, LedgerPlanAttestationCredentialMode.REQUIRED),
  /**
   * The dedicated read-only execution completed without mutation authority or an aggregate
   * operation.
   */
  READ_ONLY(
      LedgerPlanAttestationCommitMode.MUST_BE_NULL, LedgerPlanAttestationCredentialMode.PROHIBITED),
  /**
   * The mutation-capable execution committed successfully but no child mutation became durable, for
   * example because every requested mutation was an idempotent replay.
   */
  NO_DURABLE_CHILD_MUTATION(
      LedgerPlanAttestationCommitMode.MUST_BE_NULL, LedgerPlanAttestationCredentialMode.REQUIRED);

  private final LedgerPlanAttestationCommitMode attestationCommitMode;
  private final LedgerPlanAttestationCredentialMode attestationCredentialMode;

  LedgerPlanAttestationDisposition(
      LedgerPlanAttestationCommitMode attestationCommitMode,
      LedgerPlanAttestationCredentialMode attestationCredentialMode) {
    this.attestationCommitMode = attestationCommitMode;
    this.attestationCredentialMode = attestationCredentialMode;
  }

  /** Returns the stable wire value for this disposition. */
  @Override
  public String wireValue() {
    return switch (this) {
      case APPENDED -> "appended";
      case READ_ONLY -> "read-only";
      case NO_DURABLE_CHILD_MUTATION -> "no-durable-child-mutation";
    };
  }

  /** Returns the required aggregate-commitment field mode for this successful disposition. */
  public LedgerPlanAttestationCommitMode attestationCommitMode() {
    return attestationCommitMode;
  }

  /**
   * Whether this disposition requires the resulting plan payload to carry an attestation commit.
   */
  public boolean requiresAttestationCommit() {
    return attestationCommitMode.requiresAttestationCommit();
  }

  /** Returns the required attestation-credential mode for this successful disposition. */
  public LedgerPlanAttestationCredentialMode attestationCredentialMode() {
    return attestationCredentialMode;
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerPlanAttestationDisposition.class);
  }

  /** Parses one stable wire value. */
  public static LedgerPlanAttestationDisposition fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerPlanAttestationDisposition.class,
        wireValue,
        "Unsupported ledger plan attestation disposition");
  }
}
