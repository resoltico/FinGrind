package dev.erst.fingrind.contract.runtime;

import java.util.Map;

/** Owns descriptor metadata for attestation credential, admission, and review failures. */
final class ContractAttestationErrorDescriptors {
  static final ContractErrorDescriptorDefinition INVALID_ATTESTATION_CREDENTIAL =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-attestation-credential",
          "Attested-book authorization refused because a required attestation credential selection is missing or invalid.",
          6);
  static final ContractErrorDescriptorDefinition ATTESTATION_CREDENTIALS_NOT_ALLOWED =
      ContractErrorDescriptorDefinitions.structuralInvalid(
          "attestation-credentials-not-allowed",
          "Query-only or assertion-only ledger-plan execution refused because attestation credentials authorize only protected-book mutations.");
  static final ContractErrorDescriptorDefinition INVALID_ATTESTATION_KEY_FILE =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-attestation-key-file",
          "Attestation credential generation or inspection refused because the selected credential-file path or its parent directory does not satisfy the credential-custody contract.",
          6);
  static final ContractErrorDescriptorDefinition STALE_HEAD =
      ContractErrorDescriptorDefinitions.precondition(
          "stale-head",
          "Attested-book mutation refused because the authenticated operation head advanced after signing and before atomic admission.",
          2);
  static final ContractErrorDescriptorDefinition ATTESTATION_REVIEW_REQUIRED =
      ContractErrorDescriptorDefinitions.precondition(
          "attestation-review-required",
          "Verification found a declared credential-compromise review condition while the caller required a clean attestation result.",
          2);
  static final ContractErrorDescriptorDefinition ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD =
      ContractErrorDescriptorDefinitions.domainSemantic(
          "attestation-review-window-exceeds-head",
          "Attestation review refused because a declared compromise-review window is not contained by the authenticated book head.");
  static final ContractErrorDescriptorDefinition CUSTODIAN_NOT_SUPPORTED =
      ContractErrorDescriptorDefinitions.unsupportedSelection(
          "custodian-not-supported",
          "Attestation-key custody refused because the selected custodian is not implemented by this FinGrind version.");

  private ContractAttestationErrorDescriptors() {}

  static void addTo(Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    definitions.put(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL, INVALID_ATTESTATION_CREDENTIAL);
    definitions.put(
        ContractErrors.Descriptor.ATTESTATION_CREDENTIALS_NOT_ALLOWED,
        ATTESTATION_CREDENTIALS_NOT_ALLOWED);
    definitions.put(
        ContractErrors.Descriptor.INVALID_ATTESTATION_KEY_FILE, INVALID_ATTESTATION_KEY_FILE);
    definitions.put(ContractErrors.Descriptor.STALE_HEAD, STALE_HEAD);
    definitions.put(
        ContractErrors.Descriptor.ATTESTATION_REVIEW_REQUIRED, ATTESTATION_REVIEW_REQUIRED);
    definitions.put(
        ContractErrors.Descriptor.ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD,
        ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD);
    definitions.put(ContractErrors.Descriptor.CUSTODIAN_NOT_SUPPORTED, CUSTODIAN_NOT_SUPPORTED);
  }
}
