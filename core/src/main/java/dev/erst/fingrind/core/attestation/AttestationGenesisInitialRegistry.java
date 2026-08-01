package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates the genesis policies and grants that establish the initial authorization registry. */
final class AttestationGenesisInitialRegistry {
  private static final int CAPABILITY_GRANT_RECORD_TYPE = 0x0003;
  private static final int POLICY_RULE_RECORD_TYPE = 0x0005;
  private static final int CREATE_MUTATION = 0;
  private static final String GRANT_STATE = "grant";

  private AttestationGenesisInitialRegistry() {}

  static InitialRegistry requireValid(
      AttestationPreimage effectPreimage, List<AttestationFounder> founders) {
    return new InitialRegistry(
        requireInitialPolicies(effectPreimage, founders.size()),
        requireInitialGrants(effectPreimage, founders));
  }

  private static List<AttestationPolicyRule> requireInitialPolicies(
      AttestationPreimage effectPreimage, int founderCount) {
    List<AttestationPreimage.Fact> policies =
        AttestationPreimageFields.records(effectPreimage, POLICY_RULE_RECORD_TYPE);
    if (policies.size() != AttestationCapability.values().length) {
      throw failure();
    }
    List<AttestationPolicyRule> rules = new java.util.ArrayList<>(policies.size());
    for (AttestationPreimage.Fact policy : policies) {
      rules.add(requireInitialPolicy(policy, founderCount));
    }
    return List.copyOf(rules);
  }

  private static AttestationPolicyRule requireInitialPolicy(
      AttestationPreimage.Fact policy, int founderCount) {
    if (AttestationPreimageValueReader.mutation(policy, 0, failureType()) != CREATE_MUTATION) {
      throw failure();
    }
    AttestationCapability capability =
        capability(AttestationPreimageValueReader.token(policy, 1, failureType()));
    int quorum = AttestationPreimageValueReader.unsigned16(policy, 2, failureType());
    if (quorum < 1 || quorum > founderCount) {
      throw failure();
    }
    return new AttestationPolicyRule(BigInteger.ZERO, capability, quorum);
  }

  private static List<AttestationCapabilityGrant> requireInitialGrants(
      AttestationPreimage effectPreimage, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> grantFacts =
        AttestationPreimageFields.records(effectPreimage, CAPABILITY_GRANT_RECORD_TYPE);
    if (grantFacts.size() != founders.size() * AttestationCapability.values().length) {
      throw failure();
    }
    Set<UUID> founderIds = new HashSet<>();
    for (AttestationFounder founder : founders) {
      founderIds.add(founder.principalId());
    }
    List<AttestationCapabilityGrant> initialGrants = new java.util.ArrayList<>(grantFacts.size());
    for (AttestationPreimage.Fact grant : grantFacts) {
      InitialGrant declared = requireInitialGrant(grant, founderIds);
      initialGrants.add(
          new AttestationCapabilityGrant(
              BigInteger.ZERO,
              declared.principalId(),
              declared.capability(),
              AttestationGrantState.GRANT));
    }
    return List.copyOf(initialGrants);
  }

  private static InitialGrant requireInitialGrant(
      AttestationPreimage.Fact grant, Set<UUID> founderIds) {
    if (AttestationPreimageValueReader.mutation(grant, 0, failureType()) != CREATE_MUTATION) {
      throw failure();
    }
    if (!GRANT_STATE.equals(AttestationPreimageValueReader.token(grant, 3, failureType()))) {
      throw failure();
    }
    UUID principalId = AttestationPreimageValueReader.uuid(grant, 1, failureType());
    if (!founderIds.contains(principalId)) {
      throw failure();
    }
    return new InitialGrant(
        principalId, capability(AttestationPreimageValueReader.token(grant, 2, failureType())));
  }

  private static AttestationCapability capability(String token) {
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw failure();
  }

  private static AttestationAuthorizationFailure failureType() {
    return AttestationAuthorizationFailure.GENESIS_INVALID;
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(failureType());
  }

  /* Immutable registry facts established by the unanimous genesis envelope. */
  record InitialRegistry(
      List<AttestationPolicyRule> policyRules, List<AttestationCapabilityGrant> grants) {
    InitialRegistry {
      policyRules = List.copyOf(policyRules);
      grants = List.copyOf(grants);
    }
  }

  private record InitialGrant(UUID principalId, AttestationCapability capability) {}
}
