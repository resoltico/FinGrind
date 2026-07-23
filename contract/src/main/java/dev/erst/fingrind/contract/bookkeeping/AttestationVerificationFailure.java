package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Closed public vocabulary for structural protected-book and receipt verification refusals. */
public enum AttestationVerificationFailure {
  UNSUPPORTED_VERSION(
      "attestation-unsupported-version",
      "The attestation artifact declares an unsupported version."),
  PREIMAGE_INVALID(
      "attestation-preimage-invalid",
      "The attestation artifact has an invalid canonical preimage."),
  PREVIOUS_HEAD_INVALID(
      "attestation-previous-head-invalid",
      "The attestation chain has an invalid previous-head link."),
  REQUEST_PROFILE_INVALID(
      "attestation-request-profile-invalid",
      "The signed request facts do not match their operation profile."),
  UNKNOWN_OPERATION_KIND(
      "attestation-unknown-operation-kind",
      "The attestation artifact names an unknown operation kind."),
  ENVELOPE_ORDER_INVALID(
      "attestation-envelope-order-invalid", "The attestation envelope order is not canonical."),
  QUORUM_BELOW(
      "attestation-quorum-below", "The attestation envelope has fewer signatures than its quorum."),
  QUORUM_EXCESS(
      "attestation-quorum-excess", "The attestation envelope has more signatures than its quorum."),
  DUPLICATE_PRINCIPAL(
      "attestation-duplicate-principal", "The attestation envelope repeats one principal."),
  DUPLICATE_KEY(
      "attestation-duplicate-key", "The attestation envelope repeats one credential key."),
  KEY_NOT_ENROLLED(
      "attestation-key-not-enrolled",
      "The attestation envelope uses a key not enrolled at that position."),
  KEY_REVOKED("attestation-key-revoked", "The attestation envelope uses a revoked key."),
  KEY_SUPERSEDED(
      "attestation-key-superseded",
      "The attestation envelope uses a credential superseded by rollover."),
  KEY_PRINCIPAL_MISMATCH(
      "attestation-key-principal-mismatch",
      "The attestation key does not belong to its asserted principal."),
  KEY_ALGORITHM_INVALID(
      "attestation-key-algorithm-invalid", "The attestation key algorithm is not accepted."),
  SIGNATURE_INVALID(
      "attestation-signature-invalid", "The attestation envelope contains an invalid signature."),
  CAPABILITY_INVALID(
      "attestation-capability-invalid", "The attestation authorization capability is not valid."),
  POLICY_CAPACITY_INVALID(
      "attestation-policy-capacity-invalid",
      "The requested authority change would leave an effective quorum unsatisfiable."),
  CREDENTIAL_PURPOSE_INVALID(
      "attestation-credential-purpose-invalid", "The attestation credential purpose is not valid."),
  SYSTEM_DERIVATION_INVALID(
      "attestation-system-derivation-invalid", "The autonomous-system derivation is not valid."),
  GENESIS_INVALID("attestation-genesis-invalid", "The attestation genesis operation is not valid."),
  MANIFEST_INVALID("attestation-manifest-invalid", "The attestation backup manifest is not valid."),
  RECEIPT_INVALID("attestation-receipt-invalid", "The attestation receipt is not valid."),
  RECEIPT_ARTIFACT_INVALID(
      "receipt-artifact-invalid", "The selected receipt artifact cannot be verified.");

  private static final List<ContractResponse.RejectionDescriptor> REJECTION_DESCRIPTORS =
      Arrays.stream(values())
          .map(
              failure ->
                  new ContractResponse.RejectionDescriptor(
                      failure.wireCode,
                      ContractResponse.FailureCategory.STRUCTURAL_INVALID,
                      failure.description))
          .toList();

  private final String wireCode;
  private final String description;

  AttestationVerificationFailure(String wireCode, String description) {
    this.wireCode = wireCode;
    this.description = description;
  }

  /** Returns the stable public code emitted in a rejected verification envelope. */
  public String wireCode() {
    return wireCode;
  }

  /** Returns the public explanation for this exact structural verification refusal. */
  public String description() {
    return description;
  }

  /** Returns descriptors for every exact structural verification refusal. */
  public static List<ContractResponse.RejectionDescriptor> rejectionDescriptors() {
    return REJECTION_DESCRIPTORS;
  }

  /**
   * Resolves one exact public verification code without accepting aliases or normalized variants.
   */
  public static AttestationVerificationFailure fromWireCode(String wireCode) {
    String requestedCode = Objects.requireNonNull(wireCode, "wireCode");
    return Arrays.stream(values())
        .filter(failure -> failure.wireCode.equals(requestedCode))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown attestation verification failure code: " + requestedCode));
  }

  /** Validates and returns one exact public verification code. */
  public static String requireWireCode(String wireCode) {
    return fromWireCode(wireCode).wireCode;
  }
}
