package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.util.List;

/** Maps verified immutable authority facts into stable CLI payload and text values. */
final class CliAttestationPayloadMapper {
  private CliAttestationPayloadMapper() {}

  static CliAttestationJsonModels.AttestationRegistryPayload registryPayload(
      AttestationRegistryInspection registry) {
    return new CliAttestationJsonModels.AttestationRegistryPayload(
        registry.credentials().stream()
            .map(
                credential ->
                    new CliAttestationJsonModels.AttestationCredentialPayload(
                        credential.principalId().toString(),
                        credential.keyId(),
                        credential.credentialSpki(),
                        credential.credentialPurpose(),
                        credential.bindingAction(),
                        credential.acceptedOrder().toString(),
                        credential.predecessorKeyId(),
                        credential.state()))
            .toList(),
        registry.capabilityPolicies().stream()
            .map(
                policy ->
                    new CliAttestationJsonModels.AttestationCapabilityPolicyPayload(
                        policy.capability(),
                        policy.quorum(),
                        policy.eligiblePrincipalCount(),
                        policy.eligibleOperatorPrincipalCount(),
                        policy.eligibleSystemPrincipalCount()))
            .toList(),
        registry.principalCapabilities().stream()
            .map(
                capability ->
                    new CliAttestationJsonModels.AttestationPrincipalCapabilityPayload(
                        capability.principalId().toString(),
                        capability.capability(),
                        capability.eligible()))
            .toList(),
        registry.systemWorkflowPolicies().stream()
            .map(
                policy ->
                    new CliAttestationJsonModels.AttestationSystemWorkflowPolicyPayload(
                        policy.workflowId().toString(),
                        policy.workflowKind(),
                        policy.resultHoldingAccountCode(),
                        policy.capitalAccountCode(),
                        policy.retainedResultAccountCode(),
                        policy.active(),
                        policy.acceptedOrder().toString()))
            .toList());
  }

  static String renderedCredentials(List<AttestationRegistryInspection.Credential> credentials) {
    return credentials.isEmpty()
        ? "(none)"
        : CliTextFormat.renderBulletedBlock(
            credentials.stream()
                .map(
                    credential ->
                        "principalId="
                            + credential.principalId()
                            + "\n  keyId="
                            + credential.keyId()
                            + "\n  purpose="
                            + credential.credentialPurpose()
                            + "\n  state="
                            + credential.state())
                .toList(),
            Integer.MAX_VALUE);
  }

  static String renderedCapabilityPolicies(
      List<AttestationRegistryInspection.CapabilityPolicy> policies) {
    return policies.isEmpty()
        ? "(none)"
        : CliTextFormat.renderBulletedBlock(
            policies.stream()
                .map(
                    policy ->
                        "capability="
                            + policy.capability()
                            + "\n  quorum="
                            + policy.quorum()
                            + "\n  eligiblePrincipals="
                            + policy.eligiblePrincipalCount()
                            + "\n  eligibleOperatorPrincipals="
                            + policy.eligibleOperatorPrincipalCount()
                            + "\n  eligibleSystemPrincipals="
                            + policy.eligibleSystemPrincipalCount())
                .toList(),
            Integer.MAX_VALUE);
  }
}
