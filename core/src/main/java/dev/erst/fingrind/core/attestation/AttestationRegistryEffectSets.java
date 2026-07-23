package dev.erst.fingrind.core.attestation;

import java.util.List;

/** Effect facts partitioned by their closed registry-impacting record types. */
record AttestationRegistryEffectSets(
    List<AttestationPreimage.Fact> bindings,
    List<AttestationPreimage.Fact> retirements,
    List<AttestationPreimage.Fact> grants,
    List<AttestationPreimage.Fact> policyRules,
    List<AttestationPreimage.Fact> workflowPolicies) {
  private static final int BINDING_EFFECT = 0x0002;
  private static final int GRANT_EFFECT = 0x0003;
  private static final int RETIREMENT_EFFECT = 0x0009;
  private static final int POLICY_EFFECT = 0x0005;
  private static final int WORKFLOW_EFFECT = 0x0008;

  /** Partitions only catalog-recognized registry effects from one canonical effect preimage. */
  static AttestationRegistryEffectSets from(AttestationPreimage effectPreimage) {
    return new AttestationRegistryEffectSets(
        AttestationPreimageFields.records(effectPreimage, BINDING_EFFECT),
        AttestationPreimageFields.records(effectPreimage, RETIREMENT_EFFECT),
        AttestationPreimageFields.records(effectPreimage, GRANT_EFFECT),
        AttestationPreimageFields.records(effectPreimage, POLICY_EFFECT),
        AttestationPreimageFields.records(effectPreimage, WORKFLOW_EFFECT));
  }
}
