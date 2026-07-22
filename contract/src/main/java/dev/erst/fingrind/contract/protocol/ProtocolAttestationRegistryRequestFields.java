package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Set;

/** Canonical JSON field names for public attestation credential and policy mutations. */
public final class ProtocolAttestationRegistryRequestFields {
  public static final String PRINCIPAL_ID = "principalId";
  public static final String CREDENTIAL_SPKI = "credentialSpki";
  public static final String PREDECESSOR_CREDENTIAL_SPKI = "predecessorCredentialSpki";
  public static final String CREDENTIAL_PURPOSE = "credentialPurpose";
  public static final String REASON = "reason";
  public static final String POLICY_RULES = "policyRules";
  public static final String CAPABILITY_GRANTS = "capabilityGrants";
  public static final String SYSTEM_WORKFLOW_POLICIES = "systemWorkflowPolicies";
  public static final String CAPABILITY = "capability";
  public static final String QUORUM = "quorum";
  public static final String STATE = "state";
  public static final String WORKFLOW_ID = "workflowId";
  public static final String WORKFLOW_KIND = "workflowKind";
  public static final String RESULT_HOLDING_ACCOUNT_CODE = "resultHoldingAccountCode";
  public static final String CAPITAL_ACCOUNT_CODE = "capitalAccountCode";
  public static final String RETAINED_RESULT_ACCOUNT_CODE = "retainedResultAccountCode";
  public static final String ACTIVE = "active";

  private ProtocolAttestationRegistryRequestFields() {}

  /** Returns the accepted top-level request fields for one credential enrollment. */
  public static Set<String> enrollKeyFields() {
    return Set.of(PRINCIPAL_ID, CREDENTIAL_SPKI, CREDENTIAL_PURPOSE);
  }

  /** Returns the accepted top-level request fields for one credential rollover. */
  public static Set<String> rolloverKeyFields() {
    return Set.of(PRINCIPAL_ID, CREDENTIAL_SPKI, CREDENTIAL_PURPOSE, PREDECESSOR_CREDENTIAL_SPKI);
  }

  /** Returns the accepted top-level request fields for one credential revocation. */
  public static Set<String> revokeKeyFields() {
    return Set.of(PRINCIPAL_ID, CREDENTIAL_SPKI, REASON);
  }

  /** Returns the accepted top-level request fields for one policy mutation. */
  public static Set<String> alterPolicyFields() {
    return Set.of(POLICY_RULES, CAPABILITY_GRANTS, SYSTEM_WORKFLOW_POLICIES);
  }

  /** Returns the accepted fields for each policy-rules array member. */
  public static Set<String> policyRuleFields() {
    return Set.of(CAPABILITY, QUORUM);
  }

  /** Returns the accepted fields for each capability-grants array member. */
  public static Set<String> capabilityGrantFields() {
    return Set.of(PRINCIPAL_ID, CAPABILITY, STATE);
  }

  /** Returns the accepted fields for each system-workflow-policies array member. */
  public static Set<String> systemWorkflowPolicyFields() {
    return Set.of(
        WORKFLOW_ID,
        WORKFLOW_KIND,
        RESULT_HOLDING_ACCOUNT_CODE,
        CAPITAL_ACCOUNT_CODE,
        RETAINED_RESULT_ACCOUNT_CODE,
        ACTIVE);
  }

  /** Returns the top-level optional array fields in stable documentation order. */
  public static List<String> alterPolicyArrayFields() {
    return List.of(POLICY_RULES, CAPABILITY_GRANTS, SYSTEM_WORKFLOW_POLICIES);
  }
}
