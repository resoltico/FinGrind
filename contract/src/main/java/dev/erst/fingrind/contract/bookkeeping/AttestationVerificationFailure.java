package dev.erst.fingrind.contract.bookkeeping;

import static dev.erst.fingrind.contract.bookkeeping.AttestationDiagnosticReachability.MANIFEST_ADMISSION;
import static dev.erst.fingrind.contract.bookkeeping.AttestationDiagnosticReachability.OPERATION_EVIDENCE;
import static dev.erst.fingrind.contract.bookkeeping.AttestationDiagnosticReachability.RECEIPT_ADMISSION;
import static dev.erst.fingrind.contract.bookkeeping.AttestationDiagnosticReachability.RECEIPT_ARTIFACT;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Closed public vocabulary for protected-book attestation verification and authorization refusals.
 */
public enum AttestationVerificationFailure {
  UNSUPPORTED_VERSION(
      "attestation-unsupported-version",
      "The attestation artifact declares an unsupported version.",
      "The attestation artifact declares an unsupported version.",
      "Use a FinGrind release that supports the declared attestation version, then rerun the operation.",
      "Use a FinGrind release that supports the declared attestation version before evaluating this immutable evidence.",
      OPERATION_EVIDENCE),
  PREIMAGE_INVALID(
      "attestation-preimage-invalid",
      "The attestation artifact has an invalid canonical preimage.",
      "The attestation artifact has an invalid canonical preimage.",
      "Recreate the attestation artifact through FinGrind without altering its signed preimage.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; do not alter the attestation chain.",
      OPERATION_EVIDENCE),
  PREVIOUS_HEAD_INVALID(
      "attestation-previous-head-invalid",
      "The attestation chain has an invalid previous-head link.",
      "The attestation chain has an invalid previous-head link.",
      "Refresh the live attestation head, then regenerate and sign the operation against that head.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; do not alter the attestation chain.",
      OPERATION_EVIDENCE),
  REQUEST_PROFILE_INVALID(
      "attestation-request-profile-invalid",
      "The signed request facts do not match their operation profile.",
      "The signed request facts do not match their operation profile.",
      "Regenerate the request with the canonical facts for its operation kind, then sign that exact request.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; do not alter the attestation chain.",
      OPERATION_EVIDENCE),
  UNKNOWN_OPERATION_KIND(
      "attestation-unknown-operation-kind",
      "The attestation artifact names an unknown operation kind.",
      "The attestation artifact names an unknown operation kind.",
      "Use a supported FinGrind operation kind, then regenerate the attestation request.",
      "Use a FinGrind release that supports the declared operation kind if this evidence is expected; otherwise preserve it and restore a verified independently retained backup.",
      OPERATION_EVIDENCE),
  ENVELOPE_ORDER_INVALID(
      "attestation-envelope-order-invalid",
      "The attestation envelope order is not canonical.",
      "The attestation envelope order is not canonical.",
      "Order selected credentials by ascending credential key ID before signing the request.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; do not alter the attestation chain.",
      OPERATION_EVIDENCE),
  QUORUM_BELOW(
      "attestation-quorum-below",
      "The attestation envelope provides fewer signatures than the required attestation quorum.",
      "The selected signing credentials provide fewer signatures than the required attestation quorum.",
      "Provide exactly the required number of eligible signing credentials, then rerun the action.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; historical signatures cannot be added in place.",
      OPERATION_EVIDENCE),
  QUORUM_EXCESS(
      "attestation-quorum-excess",
      "The attestation envelope provides more signatures than the required attestation quorum.",
      "The selected signing credentials provide more signatures than the required attestation quorum.",
      "Provide exactly the required number of eligible signing credentials, then rerun the action.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; historical signatures cannot be removed in place.",
      OPERATION_EVIDENCE),
  DUPLICATE_PRINCIPAL(
      "attestation-duplicate-principal",
      "The attestation envelope repeats one principal.",
      "The attestation envelope repeats one principal.",
      "Select distinct principals, with one credential for each principal, then rerun the action.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; historical signatures cannot be rewritten in place.",
      OPERATION_EVIDENCE),
  DUPLICATE_KEY(
      "attestation-duplicate-key",
      "The attestation envelope repeats one credential key.",
      "The attestation envelope repeats one credential key.",
      "Select each credential at most once, then rerun the action.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; historical signatures cannot be rewritten in place.",
      OPERATION_EVIDENCE),
  KEY_NOT_ENROLLED(
      "attestation-key-not-enrolled",
      "The attestation envelope uses a key not enrolled at the resolving attestation position.",
      "The selected credential is not enrolled at the live book head.",
      "Use a credential enrolled at the live book head, or enroll it before retrying.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical credential binding cannot be added in place.",
      OPERATION_EVIDENCE),
  KEY_REVOKED(
      "attestation-key-revoked",
      "The attestation envelope uses a key revoked at the resolving attestation position.",
      "The selected credential is revoked at the live book head.",
      "Use an active credential that has not been revoked at the live book head.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical credential revocation cannot be changed in place.",
      OPERATION_EVIDENCE),
  KEY_SUPERSEDED(
      "attestation-key-superseded",
      "The attestation envelope uses a credential superseded by rollover at the resolving attestation position.",
      "The selected credential was superseded by rollover at the live book head.",
      "Use the active replacement credential for the superseded key.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical credential rollover cannot be changed in place.",
      OPERATION_EVIDENCE),
  KEY_PRINCIPAL_MISMATCH(
      "attestation-key-principal-mismatch",
      "A selected credential does not belong to its asserted principal at the resolving attestation position.",
      "A selected credential does not belong to its asserted principal at the live book head.",
      "Use the principal ID bound to the selected credential at the live book head.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical principal binding cannot be changed in place.",
      OPERATION_EVIDENCE),
  KEY_ALGORITHM_INVALID(
      "attestation-key-algorithm-invalid",
      "The attestation key algorithm is not accepted.",
      "The attestation key algorithm is not accepted.",
      "Use the accepted Ed25519 algorithm and an enrolled credential with a valid Ed25519 public key.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical credential algorithm cannot be changed in place.",
      OPERATION_EVIDENCE),
  SIGNATURE_INVALID(
      "attestation-signature-invalid",
      "The attestation envelope contains an invalid signature.",
      "The attestation envelope contains an invalid signature.",
      "Regenerate the signature with the enrolled credential over the canonical attestation payload.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical signature cannot be regenerated in place.",
      OPERATION_EVIDENCE),
  CAPABILITY_INVALID(
      "attestation-capability-invalid",
      "The attestation registry at the resolving position does not authorize the required capability for this action.",
      "The live attestation registry does not authorize the required capability for this action.",
      "Confirm that the required capability has an active policy and enough active principals with grants at the live book head, then select exactly its quorum.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical capability grant cannot be changed in place.",
      OPERATION_EVIDENCE),
  POLICY_CAPACITY_INVALID(
      "attestation-policy-capacity-invalid",
      "The requested authority change would leave an effective quorum unsatisfiable.",
      "The requested authority change would leave an effective quorum unsatisfiable.",
      "Retain enough eligible principals, including required credential purposes, to satisfy every configured quorum before retrying the authority change.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical policy cannot be repaired in place.",
      OPERATION_EVIDENCE),
  CREDENTIAL_PURPOSE_INVALID(
      "attestation-credential-purpose-invalid",
      "At least one selected credential has a purpose that is not valid for this operation's source channel.",
      "At least one selected credential has a purpose that is not valid for this operation's source channel.",
      "Use credentials whose declared purpose is valid for this operation's source channel.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical credential purpose cannot be changed in place.",
      OPERATION_EVIDENCE),
  SYSTEM_DERIVATION_INVALID(
      "attestation-system-derivation-invalid",
      "The autonomous-system derivation is not valid.",
      "The autonomous-system derivation is not valid.",
      "Run the operation through an active system workflow authorized for this operation.",
      "Preserve the invalid evidence for investigation and restore a verified independently retained backup; a historical system derivation cannot be changed in place.",
      OPERATION_EVIDENCE),
  GENESIS_INVALID(
      "attestation-genesis-invalid",
      "The attestation genesis operation is not valid.",
      "The attestation genesis operation is not valid.",
      "Regenerate the book genesis with its declared founders and canonical founder signatures.",
      "Preserve the invalid genesis evidence for investigation and restore a verified independently retained backup; genesis cannot be repaired in place.",
      OPERATION_EVIDENCE),
  MANIFEST_INVALID(
      "attestation-manifest-invalid",
      "The attestation backup manifest is not valid.",
      "The attestation backup manifest is not valid.",
      "Create a new backup pair from the protected book, then regenerate and verify its manifest.",
      "Preserve the invalid backup artifact for investigation and create a new backup pair from a verified protected book.",
      MANIFEST_ADMISSION),
  RECEIPT_INVALID(
      "attestation-receipt-invalid",
      "The attestation receipt is not valid.",
      "The attestation receipt is not valid.",
      "Regenerate the receipt from a valid protected book, then verify the selected receipt again.",
      "Preserve the invalid receipt for investigation and export a new receipt from a verified protected book.",
      RECEIPT_ADMISSION),
  RECEIPT_ARTIFACT_INVALID(
      "receipt-artifact-invalid",
      "The selected receipt artifact cannot be verified.",
      "The selected receipt artifact cannot be verified.",
      "Select an intact receipt artifact exported for this book, then verify it again.",
      "Select an intact independently retained receipt artifact or export a new receipt from a verified protected book.",
      RECEIPT_ARTIFACT);

  private final String wireCode;
  private final String description;
  private final String admissionDescription;
  private final String admissionRemediation;
  private final String verificationRemediation;
  private final AttestationDiagnosticReachability diagnosticReachability;

  AttestationVerificationFailure(
      String wireCode,
      String description,
      String admissionDescription,
      String admissionRemediation,
      String verificationRemediation,
      AttestationDiagnosticReachability diagnosticReachability) {
    this.wireCode = wireCode;
    this.description = description;
    this.admissionDescription = admissionDescription;
    this.admissionRemediation = admissionRemediation;
    this.verificationRemediation = verificationRemediation;
    this.diagnosticReachability = diagnosticReachability;
  }

  /** Returns the stable public code emitted in a rejected attestation envelope. */
  public String wireCode() {
    return wireCode;
  }

  /** Returns the context-neutral cause for this exact historical attestation refusal. */
  public String description() {
    return description;
  }

  /** Returns the exact cause as it should appear while admitting a live attested operation. */
  public String admissionDescription() {
    return admissionDescription;
  }

  /** Returns the recovery action for a live attestation-admission refusal. */
  public String admissionRemediation() {
    return admissionRemediation;
  }

  /** Returns the evidence-preserving recovery action for historical verification. */
  public String verificationRemediation() {
    return verificationRemediation;
  }

  /** Returns this failure's deliberately declared public diagnostic reachability. */
  AttestationDiagnosticReachability diagnosticReachability() {
    return diagnosticReachability;
  }

  /** Returns descriptors for every exact public attestation refusal. */
  public static List<RejectionDescriptor> rejectionDescriptors() {
    return AttestationVerificationDiagnosticCatalog.rejectionDescriptors();
  }

  /** Returns the exact live-admission diagnostic catalogs grouped by rendering context. */
  public static List<AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor>
      admissionDiagnosticContexts() {
    return AttestationVerificationDiagnosticCatalog.admissionDiagnosticContexts();
  }

  /** Returns this failure's exact diagnostic for one live-admission rendering context. */
  public AttestationDiagnosticDescriptors.DiagnosticDescriptor admissionDiagnostic(
      AdmissionContext context) {
    return AttestationVerificationDiagnosticCatalog.admissionDiagnostic(this, context);
  }

  /** Validates that this exact failure can be emitted by the given live-admission context. */
  static AttestationVerificationFailure requireAdmissionFailure(
      AttestationVerificationFailure failure, AdmissionContext context) {
    AttestationVerificationFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    checkedFailure.admissionDiagnostic(context);
    return checkedFailure;
  }

  /**
   * Returns the complete exact historical verification catalog grouped by its emitting operation.
   *
   * <p>The catalog is the single owner of the surface-specific message prefix and recovery hint;
   * transport code must project it rather than reauthoring those diagnostics.
   */
  public static List<AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor>
      verificationDiagnosticSurfaces() {
    return AttestationVerificationDiagnosticCatalog.verificationDiagnosticSurfaces();
  }

  /** Returns this failure's exact historical verification diagnostic for one supported surface. */
  public AttestationDiagnosticDescriptors.DiagnosticDescriptor verificationDiagnostic(
      OperationId surface) {
    return AttestationVerificationDiagnosticCatalog.verificationDiagnostic(this, surface);
  }

  /** Validates that this exact failure can be emitted by the given verification surface. */
  static AttestationVerificationFailure requireVerificationFailure(
      AttestationVerificationFailure failure, OperationId surface) {
    AttestationVerificationFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    checkedFailure.verificationDiagnostic(surface);
    return checkedFailure;
  }

  /** Validates that one exact failure code can be emitted by the given verification surface. */
  static String requireVerificationWireCode(String failureCode, OperationId surface) {
    return requireVerificationFailure(fromWireCode(failureCode), surface).wireCode();
  }

  /**
   * Resolves one exact public attestation refusal code without accepting aliases or normalized
   * variants.
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
}
