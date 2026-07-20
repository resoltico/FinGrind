package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.List;

/** Verifies that each authority effect is an exact projection of a same-kind request fact. */
final class AttestationRegistryEffectProjection {
  private static final int BINDING_REQUEST = 0x0180;
  private static final int REVOCATION_REQUEST = 0x0181;
  private static final int POLICY_REQUEST = 0x0182;
  private static final int GRANT_REQUEST = 0x0183;
  private static final int WORKFLOW_REQUEST = 0x0184;

  private AttestationRegistryEffectProjection() {}

  /** Requires a one-to-one request/effect projection for every registry effect group. */
  static void require(AttestationPreimage requestPreimage, AttestationRegistryEffectSets effects) {
    requireProjection(
        AttestationPreimageFields.records(requestPreimage, BINDING_REQUEST),
        effects.bindings(),
        0,
        1,
        1,
        2,
        2,
        3,
        3,
        4,
        4,
        5,
        5,
        6);
    requireProjection(
        AttestationPreimageFields.records(requestPreimage, REVOCATION_REQUEST),
        effects.revocations(),
        0,
        1,
        1,
        2,
        2,
        3);
    requireProjection(
        AttestationPreimageFields.records(requestPreimage, GRANT_REQUEST),
        effects.grants(),
        0,
        1,
        1,
        2,
        2,
        3);
    requireProjection(
        AttestationPreimageFields.records(requestPreimage, POLICY_REQUEST),
        effects.policyRules(),
        0,
        1,
        1,
        2);
    requireProjection(
        AttestationPreimageFields.records(requestPreimage, WORKFLOW_REQUEST),
        effects.workflowPolicies(),
        0,
        1,
        1,
        2,
        2,
        3,
        3,
        4,
        4,
        5,
        5,
        6);
  }

  private static void requireProjection(
      List<AttestationPreimage.Fact> requests,
      List<AttestationPreimage.Fact> effects,
      int... matchingFieldIndexes) {
    if (requests.size() != effects.size()) {
      throw failure();
    }
    for (AttestationPreimage.Fact effect : effects) {
      if (!matchingRequest(requests, effect, matchingFieldIndexes)) {
        throw failure();
      }
    }
  }

  private static boolean matchingRequest(
      List<AttestationPreimage.Fact> requests,
      AttestationPreimage.Fact effect,
      int[] matchingFieldIndexes) {
    for (AttestationPreimage.Fact request : requests) {
      if (matchesProjection(request, effect, matchingFieldIndexes)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesProjection(
      AttestationPreimage.Fact request,
      AttestationPreimage.Fact effect,
      int[] matchingFieldIndexes) {
    for (int index = 0; index < matchingFieldIndexes.length; index += 2) {
      if (!Arrays.equals(
          request.fields().get(matchingFieldIndexes[index]).encoded(),
          effect.fields().get(matchingFieldIndexes[index + 1]).encoded())) {
        return false;
      }
    }
    return true;
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
