package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Executable request scaffolds for attestation credential and policy administration. */
public final class ContractAttestationRegistryTemplates {
  public static final String EXAMPLE_CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI";
  public static final String EXAMPLE_REPLACEMENT_CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlM";
  public static final String EXAMPLE_PRINCIPAL_ID = "018f0000-0000-7000-8000-000000000003";

  private ContractAttestationRegistryTemplates() {}

  /** Minimal credential-enrollment request using the lowercase operator purpose token. */
  public record EnrollKeyTemplateDescriptor(
      String principalId, String credentialSpki, String credentialPurpose)
      implements TemplateDescriptorType {
    public EnrollKeyTemplateDescriptor {
      principalId = ContractDescriptorValidation.requireText(principalId, "principalId");
      credentialSpki = ContractDescriptorValidation.requireText(credentialSpki, "credentialSpki");
      credentialPurpose =
          ContractDescriptorValidation.requireText(credentialPurpose, "credentialPurpose");
    }
  }

  /** Minimal replacement-credential request with both public credential identities explicit. */
  public record RolloverKeyTemplateDescriptor(
      String principalId,
      String credentialSpki,
      String credentialPurpose,
      String predecessorCredentialSpki)
      implements TemplateDescriptorType {
    public RolloverKeyTemplateDescriptor {
      principalId = ContractDescriptorValidation.requireText(principalId, "principalId");
      credentialSpki = ContractDescriptorValidation.requireText(credentialSpki, "credentialSpki");
      credentialPurpose =
          ContractDescriptorValidation.requireText(credentialPurpose, "credentialPurpose");
      predecessorCredentialSpki =
          ContractDescriptorValidation.requireText(
              predecessorCredentialSpki, "predecessorCredentialSpki");
    }
  }

  /** Minimal irreversible credential-revocation request. */
  public record RevokeKeyTemplateDescriptor(
      String principalId, String credentialSpki, String reason) implements TemplateDescriptorType {
    public RevokeKeyTemplateDescriptor {
      principalId = ContractDescriptorValidation.requireText(principalId, "principalId");
      credentialSpki = ContractDescriptorValidation.requireText(credentialSpki, "credentialSpki");
      reason = ContractDescriptorValidation.requireText(reason, "reason");
    }
  }

  /** Minimal policy mutation with one explicit effective quorum rule. */
  public record AlterPolicyTemplateDescriptor(
      List<PolicyRuleTemplateDescriptor> policyRules,
      List<CapabilityGrantTemplateDescriptor> capabilityGrants,
      List<SystemWorkflowPolicyTemplateDescriptor> systemWorkflowPolicies)
      implements TemplateDescriptorType {
    public AlterPolicyTemplateDescriptor {
      policyRules = ContractDescriptorValidation.copyList(policyRules, "policyRules");
      capabilityGrants =
          ContractDescriptorValidation.copyList(capabilityGrants, "capabilityGrants");
      systemWorkflowPolicies =
          ContractDescriptorValidation.copyList(systemWorkflowPolicies, "systemWorkflowPolicies");
      if (policyRules.isEmpty() && capabilityGrants.isEmpty() && systemWorkflowPolicies.isEmpty()) {
        throw new IllegalArgumentException(
            "An attestation policy template must contain one change.");
      }
    }
  }

  /** One lowercase capability/quorum rule accepted by alter-policy. */
  public record PolicyRuleTemplateDescriptor(String capability, int quorum)
      implements TemplateDescriptorType {
    public PolicyRuleTemplateDescriptor {
      capability = ContractDescriptorValidation.requireText(capability, "capability");
      if (quorum < 1) {
        throw new IllegalArgumentException("quorum must be positive.");
      }
    }
  }

  /** One principal capability-state decision accepted by alter-policy. */
  public record CapabilityGrantTemplateDescriptor(
      String principalId, String capability, String state) implements TemplateDescriptorType {
    public CapabilityGrantTemplateDescriptor {
      principalId = ContractDescriptorValidation.requireText(principalId, "principalId");
      capability = ContractDescriptorValidation.requireText(capability, "capability");
      state = ContractDescriptorValidation.requireText(state, "state");
    }
  }

  /** One autonomous workflow-policy decision accepted by alter-policy. */
  public record SystemWorkflowPolicyTemplateDescriptor(
      String workflowId,
      String workflowKind,
      String resultHoldingAccountCode,
      String capitalAccountCode,
      String retainedResultAccountCode,
      boolean active)
      implements TemplateDescriptorType {
    public SystemWorkflowPolicyTemplateDescriptor {
      workflowId = ContractDescriptorValidation.requireText(workflowId, "workflowId");
      workflowKind = ContractDescriptorValidation.requireText(workflowKind, "workflowKind");
      resultHoldingAccountCode =
          ContractDescriptorValidation.requireText(
              resultHoldingAccountCode, "resultHoldingAccountCode");
      capitalAccountCode =
          ContractDescriptorValidation.requireText(capitalAccountCode, "capitalAccountCode");
      retainedResultAccountCode =
          ContractDescriptorValidation.requireText(
              retainedResultAccountCode, "retainedResultAccountCode");
    }
  }
}
