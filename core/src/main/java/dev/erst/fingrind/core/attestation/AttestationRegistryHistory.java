package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilds the immutable authority ledger from accepted effects, never from mutable storage rows.
 *
 * <p>Only the four registry-bearing operation families may add their corresponding effect facts.
 * The request/effect projection checks make every authority change self-proving before it can
 * influence a later historical authorization decision.
 */
final class AttestationRegistryHistory {
  private static final int BINDING_REQUEST = 0x0180;
  private static final int REVOCATION_REQUEST = 0x0181;
  private static final int POLICY_REQUEST = 0x0182;
  private static final int GRANT_REQUEST = 0x0183;
  private static final int WORKFLOW_REQUEST = 0x0184;
  private static final int BINDING_EFFECT = 0x0002;
  private static final int GRANT_EFFECT = 0x0003;
  private static final int REVOCATION_EFFECT = 0x0004;
  private static final int POLICY_EFFECT = 0x0005;
  private static final int WORKFLOW_EFFECT = 0x0008;

  private final List<AttestationCredentialBinding> bindings = new ArrayList<>();
  private final List<AttestationCredentialRevocation> revocations = new ArrayList<>();
  private final List<AttestationCapabilityGrant> grants = new ArrayList<>();
  private final List<AttestationPolicyRule> policyRules = new ArrayList<>();
  private final List<AttestationSystemWorkflowPolicy> workflowPolicies = new ArrayList<>();

  private AttestationRegistryHistory(List<AttestationFounder> founders) {
    List<AttestationFounder> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    for (AttestationFounder founder : checkedFounders) {
      bindings.add(founder.binding());
      for (AttestationCapability capability : AttestationCapability.values()) {
        grants.add(
            new AttestationCapabilityGrant(
                BigInteger.ZERO, founder.principalId(), capability, AttestationGrantState.GRANT));
      }
    }
    for (AttestationCapability capability : AttestationCapability.values()) {
      policyRules.add(
          new AttestationPolicyRule(
              BigInteger.ZERO, capability, capability.genesisQuorum(checkedFounders.size())));
    }
  }

  private AttestationRegistryHistory(AttestationRegistryHistory source) {
    bindings.addAll(source.bindings);
    revocations.addAll(source.revocations);
    grants.addAll(source.grants);
    policyRules.addAll(source.policyRules);
    workflowPolicies.addAll(source.workflowPolicies);
  }

  static AttestationRegistryHistory genesis(List<AttestationFounder> founders) {
    return new AttestationRegistryHistory(founders);
  }

  AttestationRegistry registry() {
    try {
      return AttestationRegistry.fromVerifierFacts(
          bindings, revocations, grants, policyRules, workflowPolicies);
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw profileFailure();
    }
  }

  AttestationRegistryHistory preview(
      AttestationOperationKind operationKind,
      BigInteger operationOrder,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationRegistryHistory next = new AttestationRegistryHistory(this);
    next.accept(operationKind, operationOrder, requestPreimage, effectPreimage);
    return next;
  }

  void accept(
      AttestationOperationKind operationKind,
      BigInteger operationOrder,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    Objects.requireNonNull(operationKind, "operationKind");
    BigInteger checkedOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
    Objects.requireNonNull(requestPreimage, "requestPreimage");
    Objects.requireNonNull(effectPreimage, "effectPreimage");
    List<AttestationPreimage.Fact> bindingEffects = records(effectPreimage, BINDING_EFFECT);
    List<AttestationPreimage.Fact> revocationEffects = records(effectPreimage, REVOCATION_EFFECT);
    List<AttestationPreimage.Fact> grantEffects = records(effectPreimage, GRANT_EFFECT);
    List<AttestationPreimage.Fact> policyEffects = records(effectPreimage, POLICY_EFFECT);
    List<AttestationPreimage.Fact> workflowEffects = records(effectPreimage, WORKFLOW_EFFECT);
    requireOperationOwnsRegistryEffects(
        operationKind,
        bindingEffects,
        revocationEffects,
        grantEffects,
        policyEffects,
        workflowEffects);

    List<AttestationPreimage.Fact> bindingRequests = records(requestPreimage, BINDING_REQUEST);
    List<AttestationPreimage.Fact> revocationRequests =
        records(requestPreimage, REVOCATION_REQUEST);
    List<AttestationPreimage.Fact> grantRequests = records(requestPreimage, GRANT_REQUEST);
    List<AttestationPreimage.Fact> policyRequests = records(requestPreimage, POLICY_REQUEST);
    List<AttestationPreimage.Fact> workflowRequests = records(requestPreimage, WORKFLOW_REQUEST);
    requireProjection(bindingRequests, bindingEffects, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6);
    requireProjection(revocationRequests, revocationEffects, 0, 1, 1, 2, 2, 3);
    requireProjection(grantRequests, grantEffects, 0, 1, 1, 2, 2, 3);
    requireProjection(policyRequests, policyEffects, 0, 1, 1, 2);
    requireProjection(workflowRequests, workflowEffects, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6);

    for (AttestationPreimage.Fact binding : bindingEffects) {
      bindings.add(binding(checkedOrder, binding));
    }
    for (AttestationPreimage.Fact revocation : revocationEffects) {
      revocations.add(revocation(checkedOrder, revocation));
    }
    for (AttestationPreimage.Fact grant : grantEffects) {
      grants.add(grant(checkedOrder, grant));
    }
    for (AttestationPreimage.Fact policy : policyEffects) {
      policyRules.add(policy(checkedOrder, policy));
    }
    for (AttestationPreimage.Fact workflow : workflowEffects) {
      workflowPolicies.add(workflow(checkedOrder, workflow));
    }
    registry();
  }

  private static void requireOperationOwnsRegistryEffects(
      AttestationOperationKind operationKind,
      List<AttestationPreimage.Fact> bindingEffects,
      List<AttestationPreimage.Fact> revocationEffects,
      List<AttestationPreimage.Fact> grantEffects,
      List<AttestationPreimage.Fact> policyEffects,
      List<AttestationPreimage.Fact> workflowEffects) {
    boolean hasBinding = !bindingEffects.isEmpty();
    boolean hasRevocation = !revocationEffects.isEmpty();
    boolean hasPolicyChange =
        !grantEffects.isEmpty() || !policyEffects.isEmpty() || !workflowEffects.isEmpty();
    switch (operationKind) {
      case ENROLL_KEY -> {
        if (!hasBinding || hasRevocation || hasPolicyChange) {
          throw profileFailure();
        }
        requireBindingAction(bindingEffects, "enroll");
      }
      case ROLLOVER_KEY -> {
        if (!hasBinding || hasRevocation || hasPolicyChange) {
          throw profileFailure();
        }
        requireBindingAction(bindingEffects, "rollover");
      }
      case REVOKE_KEY -> {
        if (!hasRevocation || hasBinding || hasPolicyChange) {
          throw profileFailure();
        }
      }
      case ALTER_POLICY -> {
        if (hasBinding || hasRevocation || !hasPolicyChange) {
          throw profileFailure();
        }
      }
      default -> {
        if (hasBinding || hasRevocation || hasPolicyChange) {
          throw profileFailure();
        }
      }
    }
  }

  private static void requireBindingAction(
      List<AttestationPreimage.Fact> bindingEffects, String expectedAction) {
    for (AttestationPreimage.Fact binding : bindingEffects) {
      if (!expectedAction.equals(token(binding, 3))) {
        throw profileFailure();
      }
    }
  }

  private static void requireProjection(
      List<AttestationPreimage.Fact> requests,
      List<AttestationPreimage.Fact> effects,
      int... matchingFieldIndexes) {
    if (requests.size() != effects.size() || matchingFieldIndexes.length % 2 != 0) {
      throw profileFailure();
    }
    boolean[] consumed = new boolean[requests.size()];
    for (AttestationPreimage.Fact effect : effects) {
      int requestIndex = matchingRequest(requests, consumed, effect, matchingFieldIndexes);
      if (requestIndex < 0) {
        throw profileFailure();
      }
      consumed[requestIndex] = true;
    }
  }

  private static int matchingRequest(
      List<AttestationPreimage.Fact> requests,
      boolean[] consumed,
      AttestationPreimage.Fact effect,
      int[] matchingFieldIndexes) {
    for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
      if (consumed[requestIndex]) {
        continue;
      }
      AttestationPreimage.Fact request = requests.get(requestIndex);
      if (matchesProjection(request, effect, matchingFieldIndexes)) {
        return requestIndex;
      }
    }
    return -1;
  }

  private static boolean matchesProjection(
      AttestationPreimage.Fact request,
      AttestationPreimage.Fact effect,
      int[] matchingFieldIndexes) {
    for (int index = 0; index < matchingFieldIndexes.length; index += 2) {
      if (!java.util.Arrays.equals(
          request.fields().get(matchingFieldIndexes[index]).encoded(),
          effect.fields().get(matchingFieldIndexes[index + 1]).encoded())) {
        return false;
      }
    }
    return true;
  }

  private static AttestationCredentialBinding binding(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    AttestationCredentialBinding.BindingAction action =
        switch (token(fact, 3)) {
          case "enroll" -> AttestationCredentialBinding.BindingAction.ENROLL;
          case "rollover" -> AttestationCredentialBinding.BindingAction.ROLLOVER;
          default -> throw profileFailure();
        };
    AttestationCredentialPurpose purpose =
        switch (token(fact, 5)) {
          case "operator" -> AttestationCredentialPurpose.OPERATOR;
          case "system" -> AttestationCredentialPurpose.SYSTEM;
          default -> throw profileFailure();
        };
    return new AttestationCredentialBinding(
        operationOrder,
        uuid(fact, 1),
        hash(fact, 2),
        action,
        spki(fact, 4),
        purpose,
        optionalHash(fact, 6));
  }

  private static AttestationCredentialRevocation revocation(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    return new AttestationCredentialRevocation(operationOrder, uuid(fact, 2), hash(fact, 1));
  }

  private static AttestationCapabilityGrant grant(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    AttestationGrantState state =
        switch (token(fact, 3)) {
          case "grant" -> AttestationGrantState.GRANT;
          case "revoke" -> AttestationGrantState.REVOKE;
          default -> throw profileFailure();
        };
    return new AttestationCapabilityGrant(
        operationOrder, uuid(fact, 1), capability(fact, 2), state);
  }

  private static AttestationPolicyRule policy(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    return new AttestationPolicyRule(
        operationOrder,
        capability(fact, 1),
        AttestationPreimageValueReader.unsigned16(fact, 2, failureType()));
  }

  private static AttestationSystemWorkflowPolicy workflow(
      BigInteger operationOrder, AttestationPreimage.Fact fact) {
    return new AttestationSystemWorkflowPolicy(
        operationOrder,
        uuid(fact, 1),
        workflowKind(fact, 2),
        AttestationPreimageValueReader.text(fact, 3, failureType()),
        AttestationPreimageValueReader.optionalText(fact, 4, failureType()),
        AttestationPreimageValueReader.optionalText(fact, 5, failureType()),
        AttestationPreimageValueReader.booleanValue(fact, 6, failureType()));
  }

  private static AttestationCapability capability(AttestationPreimage.Fact fact, int fieldIndex) {
    String token = token(fact, fieldIndex);
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw profileFailure();
  }

  private static AttestationSystemWorkflowKind workflowKind(
      AttestationPreimage.Fact fact, int fieldIndex) {
    return switch (token(fact, fieldIndex)) {
      case "interim-result-sweep" -> AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP;
      case "fiscal-year-close" -> AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE;
      default -> throw profileFailure();
    };
  }

  private static List<AttestationPreimage.Fact> records(AttestationPreimage preimage, int tag) {
    return AttestationPreimageFields.records(preimage, tag);
  }

  private static String token(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.token(fact, fieldIndex, failureType());
  }

  private static UUID uuid(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.uuid(fact, fieldIndex, failureType());
  }

  private static AttestationHash hash(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.hash(fact, fieldIndex, failureType());
  }

  private static AttestationSpki spki(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.spki(fact, fieldIndex, failureType());
  }

  private static @Nullable AttestationHash optionalHash(
      AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.optionalHash(fact, fieldIndex, failureType());
  }

  private static AttestationAuthorizationFailure failureType() {
    return AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID;
  }

  private static AttestationAuthorizationException profileFailure() {
    return new AttestationAuthorizationException(failureType());
  }
}
