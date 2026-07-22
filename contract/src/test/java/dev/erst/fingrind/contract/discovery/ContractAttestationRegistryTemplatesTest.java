package dev.erst.fingrind.contract.discovery;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.AlterPolicyTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.CapabilityGrantTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.EnrollKeyTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.PolicyRuleTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.RevokeKeyTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.RolloverKeyTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractAttestationRegistryTemplates.SystemWorkflowPolicyTemplateDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers every canonical attestation-registry request scaffold and its typed boundary. */
class ContractAttestationRegistryTemplatesTest {
  @Test
  void machineContractPublishesTheExactRegistryTemplateForEverySupportedOperation() {
    EnrollKeyTemplateDescriptor enroll =
        assertInstanceOf(
            EnrollKeyTemplateDescriptor.class,
            MachineContract.attestationRegistryTemplate(OperationId.ENROLL_KEY));
    RolloverKeyTemplateDescriptor rollover =
        assertInstanceOf(
            RolloverKeyTemplateDescriptor.class,
            MachineContract.attestationRegistryTemplate(OperationId.ROLLOVER_KEY));
    RevokeKeyTemplateDescriptor revoke =
        assertInstanceOf(
            RevokeKeyTemplateDescriptor.class,
            MachineContract.attestationRegistryTemplate(OperationId.REVOKE_KEY));
    AlterPolicyTemplateDescriptor alter =
        assertInstanceOf(
            AlterPolicyTemplateDescriptor.class,
            MachineContract.attestationRegistryTemplate(OperationId.ALTER_POLICY));

    assertEquals(ContractAttestationRegistryTemplates.EXAMPLE_PRINCIPAL_ID, enroll.principalId());
    assertEquals(
        ContractAttestationRegistryTemplates.EXAMPLE_REPLACEMENT_CREDENTIAL_SPKI,
        rollover.credentialSpki());
    assertEquals(
        ContractAttestationRegistryTemplates.EXAMPLE_CREDENTIAL_SPKI, revoke.credentialSpki());
    assertEquals(List.of(new PolicyRuleTemplateDescriptor("post", 1)), alter.policyRules());
    assertThrows(
        IllegalArgumentException.class,
        () -> MachineContract.attestationRegistryTemplate(OperationId.HELP));
    assertThrows(
        NullPointerException.class, () -> MachineContract.attestationRegistryTemplate(nullOf()));
  }

  @Test
  void typedRegistryTemplatesValidateEveryAcceptedShape() {
    EnrollKeyTemplateDescriptor enroll =
        new EnrollKeyTemplateDescriptor("principal", "spki", "operator");
    RolloverKeyTemplateDescriptor rollover =
        new RolloverKeyTemplateDescriptor("principal", "replacement", "operator", "predecessor");
    RevokeKeyTemplateDescriptor revoke =
        new RevokeKeyTemplateDescriptor("principal", "spki", "reason");
    PolicyRuleTemplateDescriptor rule = new PolicyRuleTemplateDescriptor("post", 1);
    CapabilityGrantTemplateDescriptor grant =
        new CapabilityGrantTemplateDescriptor("principal", "post", "granted");
    SystemWorkflowPolicyTemplateDescriptor workflow =
        new SystemWorkflowPolicyTemplateDescriptor(
            "workflow", "interim-result-sweep", "3000", "3100", "3200", true);
    AlterPolicyTemplateDescriptor policyChange =
        new AlterPolicyTemplateDescriptor(List.of(rule), List.of(), List.of());
    AlterPolicyTemplateDescriptor capabilityChange =
        new AlterPolicyTemplateDescriptor(List.of(), List.of(grant), List.of());
    AlterPolicyTemplateDescriptor workflowChange =
        new AlterPolicyTemplateDescriptor(List.of(), List.of(), List.of(workflow));

    assertEquals("operator", enroll.credentialPurpose());
    assertEquals("predecessor", rollover.predecessorCredentialSpki());
    assertEquals("reason", revoke.reason());
    assertEquals(1, policyChange.policyRules().size());
    assertEquals(1, capabilityChange.capabilityGrants().size());
    assertEquals(1, workflowChange.systemWorkflowPolicies().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new AlterPolicyTemplateDescriptor(List.of(), List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new PolicyRuleTemplateDescriptor("post", 0));
  }
}
