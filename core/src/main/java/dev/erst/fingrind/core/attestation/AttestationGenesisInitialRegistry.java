package dev.erst.fingrind.core.attestation;

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

  static void requireValid(AttestationPreimage effectPreimage, List<AttestationFounder> founders) {
    requireInitialPolicies(effectPreimage, founders.size());
    requireInitialGrants(effectPreimage, founders);
  }

  private static void requireInitialPolicies(AttestationPreimage effectPreimage, int founderCount) {
    List<AttestationPreimage.Fact> policies =
        AttestationPreimageFields.records(effectPreimage, POLICY_RULE_RECORD_TYPE);
    if (policies.size() != AttestationCapability.values().length) {
      throw failure();
    }
    for (AttestationPreimage.Fact policy : policies) {
      requireInitialPolicy(policy, founderCount);
    }
  }

  private static void requireInitialPolicy(AttestationPreimage.Fact policy, int founderCount) {
    if (AttestationPreimageValueReader.mutation(policy, 0, failureType()) != CREATE_MUTATION) {
      throw failure();
    }
    AttestationCapability capability =
        capability(AttestationPreimageValueReader.token(policy, 1, failureType()));
    if (AttestationPreimageValueReader.unsigned16(policy, 2, failureType())
        != capability.genesisQuorum(founderCount)) {
      throw failure();
    }
  }

  private static void requireInitialGrants(
      AttestationPreimage effectPreimage, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> grants =
        AttestationPreimageFields.records(effectPreimage, CAPABILITY_GRANT_RECORD_TYPE);
    if (grants.size() != founders.size() * AttestationCapability.values().length) {
      throw failure();
    }
    Set<UUID> founderIds =
        founders.stream()
            .map(AttestationFounder::principalId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    for (AttestationPreimage.Fact grant : grants) {
      requireInitialGrant(grant, founderIds);
    }
  }

  private static void requireInitialGrant(AttestationPreimage.Fact grant, Set<UUID> founderIds) {
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
    capability(AttestationPreimageValueReader.token(grant, 2, failureType()));
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
}
