package dev.erst.fingrind.core.attestation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Validates active autonomous workflow policies declared in the genesis effect. */
final class AttestationGenesisInitialWorkflowPolicies {
  private static final int SYSTEM_WORKFLOW_POLICY_RECORD_TYPE = 0x0008;
  private static final int CREATE_MUTATION = 0;

  private AttestationGenesisInitialWorkflowPolicies() {}

  static int requireValid(AttestationPreimage effectPreimage) {
    List<AttestationPreimage.Fact> workflowPolicies =
        AttestationPreimageFields.records(effectPreimage, SYSTEM_WORKFLOW_POLICY_RECORD_TYPE);
    Set<AttestationSystemWorkflowKind> workflowKinds =
        EnumSet.noneOf(AttestationSystemWorkflowKind.class);
    for (AttestationPreimage.Fact workflowPolicy : workflowPolicies) {
      requireInitialWorkflowPolicy(workflowPolicy, workflowKinds);
    }
    return workflowPolicies.size();
  }

  private static void requireInitialWorkflowPolicy(
      AttestationPreimage.Fact workflowPolicy, Set<AttestationSystemWorkflowKind> workflowKinds) {
    if (AttestationPreimageValueReader.mutation(workflowPolicy, 0, failureType()) != CREATE_MUTATION
        || !AttestationPreimageValueReader.booleanValue(workflowPolicy, 6, failureType())) {
      throw failure();
    }
    AttestationSystemWorkflowKind workflowKind =
        workflowKind(AttestationPreimageValueReader.token(workflowPolicy, 2, failureType()));
    String resultHoldingAccountCode =
        AttestationPreimageValueReader.text(workflowPolicy, 3, failureType());
    @Nullable String capitalAccountCode =
        AttestationPreimageValueReader.optionalText(workflowPolicy, 4, failureType());
    @Nullable String retainedResultAccountCode =
        AttestationPreimageValueReader.optionalText(workflowPolicy, 5, failureType());
    requireWorkflowAccountShape(
        workflowKind, resultHoldingAccountCode, capitalAccountCode, retainedResultAccountCode);
    if (!workflowKinds.add(workflowKind)) {
      throw failure();
    }
  }

  private static void requireWorkflowAccountShape(
      AttestationSystemWorkflowKind workflowKind,
      String resultHoldingAccountCode,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode) {
    if (resultHoldingAccountCode.isBlank()) {
      throw failure();
    }
    if (workflowKind.requiresCapitalAndRetainedResultAccounts()) {
      if (capitalAccountCode == null
          || retainedResultAccountCode == null
          || capitalAccountCode.isBlank()
          || retainedResultAccountCode.isBlank()) {
        throw failure();
      }
    } else if (capitalAccountCode != null || retainedResultAccountCode != null) {
      throw failure();
    }
  }

  private static AttestationSystemWorkflowKind workflowKind(String token) {
    for (AttestationSystemWorkflowKind workflowKind : AttestationSystemWorkflowKind.values()) {
      if (workflowKind.wireToken().equals(token)) {
        return workflowKind;
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
