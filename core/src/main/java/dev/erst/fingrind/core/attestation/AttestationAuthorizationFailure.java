package dev.erst.fingrind.core.attestation;

/** Closed typed outcomes for historical attestation authorization. */
public enum AttestationAuthorizationFailure {
  UNSUPPORTED_VERSION("attestation-unsupported-version"),
  PREIMAGE_INVALID("attestation-preimage-invalid"),
  PREVIOUS_HEAD_INVALID("attestation-previous-head-invalid"),
  REQUEST_PROFILE_INVALID("attestation-request-profile-invalid"),
  UNKNOWN_OPERATION_KIND("attestation-unknown-operation-kind"),
  ENVELOPE_ORDER_INVALID("attestation-envelope-order-invalid"),
  QUORUM_BELOW("attestation-quorum-below"),
  QUORUM_EXCESS("attestation-quorum-excess"),
  DUPLICATE_PRINCIPAL("attestation-duplicate-principal"),
  DUPLICATE_KEY("attestation-duplicate-key"),
  KEY_NOT_ENROLLED("attestation-key-not-enrolled"),
  KEY_REVOKED("attestation-key-revoked"),
  KEY_PRINCIPAL_MISMATCH("attestation-key-principal-mismatch"),
  KEY_ALGORITHM_INVALID("attestation-key-algorithm-invalid"),
  SIGNATURE_INVALID("attestation-signature-invalid"),
  CAPABILITY_INVALID("attestation-capability-invalid"),
  CREDENTIAL_PURPOSE_INVALID("attestation-credential-purpose-invalid"),
  SYSTEM_DERIVATION_INVALID("attestation-system-derivation-invalid"),
  GENESIS_INVALID("attestation-genesis-invalid"),
  MANIFEST_INVALID("attestation-manifest-invalid"),
  RECEIPT_INVALID("attestation-receipt-invalid");

  private final String code;

  AttestationAuthorizationFailure(String code) {
    this.code = code;
  }

  /** Returns the stable public refusal code for this authorization failure. */
  public String code() {
    return code;
  }
}
