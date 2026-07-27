package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Validates one direct child mutation before it can be embedded in an execute-plan preimage. */
final class AttestationPlanChildMutationProfile {
  private static final Map<Integer, Integer> DIRECT_REQUEST_STEP_FIELDS =
      Map.of(0x0120, 0, 0x0124, 0, 0x0127, 0, 0x012A, 0);
  private static final Map<Integer, Integer> DIRECT_EFFECT_STEP_FIELDS = Map.of(0x0020, 2);

  private AttestationPlanChildMutationProfile() {}

  static void requireValid(AttestationPreimage childRequest, AttestationPreimage childEffect) {
    AttestationAuthorizationFailure failure =
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID;
    List<AttestationPreimage.Fact> commands =
        AttestationPreimageFields.records(childRequest, 0x0100);
    if (commands.size() != 1) {
      throw failure();
    }
    AttestationPreimage.Fact command = commands.getFirst();
    PlanChildOperation operation = allowedChildOperation(command, failure);
    if (!AttestationSourceChannel.CLI
        .wireToken()
        .equals(AttestationPreimageValueReader.token(command, 3, failure))) {
      throw failure();
    }
    AttestationOperationProfile.requireDirectProfile(
        operation.operationKind(), childRequest, childEffect);
    requireLocalDirectStepOrders(childRequest, childEffect);
    operation.requireRequestEffectLinkage(childRequest, childEffect);
  }

  private static void requireLocalDirectStepOrders(
      AttestationPreimage childRequest, AttestationPreimage childEffect) {
    requireZeroStepFields(childRequest, DIRECT_REQUEST_STEP_FIELDS);
    requireZeroStepFields(childEffect, DIRECT_EFFECT_STEP_FIELDS);
  }

  private static void requireZeroStepFields(
      AttestationPreimage preimage, Map<Integer, Integer> stepFieldIndexes) {
    for (Map.Entry<Integer, Integer> stepField : stepFieldIndexes.entrySet()) {
      for (AttestationPreimage.Fact fact :
          AttestationPreimageFields.records(preimage, stepField.getKey())) {
        if (AttestationPreimageValueReader.unsigned32(
                    fact,
                    stepField.getValue(),
                    AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)
                .signum()
            != 0) {
          throw failure();
        }
      }
    }
  }

  private static void requireAccountLinkage(
      AttestationPreimage childRequest, AttestationPreimage childEffect) {
    AttestationPreimage.Fact requestAccount = exactlyOne(childRequest, 0x0110);
    AttestationPreimage.Fact effectAccount = exactlyOne(childEffect, 0x0010);
    AttestationPreimageFactCorrespondence.requireFieldsMatch(
        requestAccount, 0, effectAccount, 1, requestAccount.fields().size());
    int mutation =
        AttestationPreimageValueReader.mutation(
            effectAccount, 0, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    if ((mutation != AttestationEffectMutation.CREATE.wireValue()
            && mutation != AttestationEffectMutation.AMEND.wireValue()
            && mutation != AttestationEffectMutation.REACTIVATE.wireValue())
        || !AttestationPreimageValueReader.booleanValue(
            effectAccount, 7, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)) {
      throw failure();
    }
    AttestationPreimageFactCorrespondence.requireMappedFacts(
        childRequest, 0x0111, 0, childEffect, 0x0011, 1);
    AttestationPreimageFactCorrespondence.requireMappedFacts(
        childRequest, 0x0112, 0, childEffect, 0x0012, 1);
    requireMatchingMutation(effectAccount, childEffect, 0x0011);
    requireMatchingMutation(effectAccount, childEffect, 0x0012);
  }

  private static void requireTaxRegistrationLinkage(
      AttestationPreimage childRequest, AttestationPreimage childEffect) {
    AttestationPreimage.Fact effectRegistration = exactlyOne(childEffect, 0x0013);
    int mutation =
        AttestationPreimageValueReader.mutation(
            effectRegistration, 0, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    if (mutation != AttestationEffectMutation.CREATE.wireValue()
        && mutation != AttestationEffectMutation.AMEND.wireValue()) {
      throw failure();
    }
    AttestationPreimageFactCorrespondence.requireMappedFacts(
        childRequest, 0x0113, 0, childEffect, 0x0013, 1);
    AttestationPreimageFactCorrespondence.requireMappedFacts(
        childRequest, 0x0114, 0, childEffect, 0x0014, 1);
    requireMatchingMutation(effectRegistration, childEffect, 0x0014);
  }

  private static void requirePostingLinkage(
      AttestationPreimage childRequest, AttestationPreimage childEffect) {
    AttestationPreimage.Fact command = exactlyOne(childRequest, 0x0100);
    AttestationPreimage.Fact requestPosting = exactlyOne(childRequest, 0x0120);
    AttestationPreimage.Fact effectPosting = exactlyOne(childEffect, 0x0020);
    if (AttestationPreimageValueReader.mutation(
            effectPosting, 0, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID)
        != AttestationEffectMutation.CREATE.wireValue()) {
      throw failure();
    }
    AttestationPreimageFactCorrespondence.requireSameField(command, 0, requestPosting, 1);
    AttestationPreimageFactCorrespondence.requireSameField(requestPosting, 1, effectPosting, 3);
    AttestationPreimageFactCorrespondence.requireSameField(requestPosting, 2, effectPosting, 6);
    AttestationPreimageFactCorrespondence.requireSameField(requestPosting, 3, effectPosting, 4);
    AttestationPreimageFactCorrespondence.requireSameField(requestPosting, 4, effectPosting, 8);
    AttestationPreimageFactCorrespondence.requireSameField(command, 1, effectPosting, 10);
    AttestationPreimageFactCorrespondence.requireSameField(command, 2, effectPosting, 11);
    AttestationPreimageFactCorrespondence.requireSameField(command, 3, effectPosting, 12);
    requirePostingEffectFacts(childRequest, 0x0124, childEffect, 0x0021, effectPosting);
    requirePostingEffectFacts(childRequest, 0x0127, childEffect, 0x0024, effectPosting);
    requirePostingEffectFacts(childRequest, 0x012A, childEffect, 0x0025, effectPosting);
    requireMatchingMutation(effectPosting, childEffect, 0x0021);
    requireMatchingMutation(effectPosting, childEffect, 0x0024);
    requireMatchingMutation(effectPosting, childEffect, 0x0025);
  }

  private static void requireMatchingMutation(
      AttestationPreimage.Fact stateEffect,
      AttestationPreimage effectPreimage,
      int companionRecordTypeTag) {
    for (AttestationPreimage.Fact companion :
        AttestationPreimageFields.records(effectPreimage, companionRecordTypeTag)) {
      AttestationPreimageFactCorrespondence.requireSameField(stateEffect, 0, companion, 0);
    }
  }

  private static void requirePostingEffectFacts(
      AttestationPreimage childRequest,
      int requestRecordTypeTag,
      AttestationPreimage childEffect,
      int effectRecordTypeTag,
      AttestationPreimage.Fact effectPosting) {
    List<AttestationPreimage.Fact> effects =
        AttestationPreimageFields.records(childEffect, effectRecordTypeTag);
    for (AttestationPreimage.Fact effect : effects) {
      AttestationPreimageFactCorrespondence.requireSameField(effectPosting, 1, effect, 1);
    }
    AttestationPreimageFactCorrespondence.requireMappedFacts(
        childRequest, requestRecordTypeTag, 1, childEffect, effectRecordTypeTag, 2);
  }

  private static AttestationPreimage.Fact exactlyOne(
      AttestationPreimage preimage, int recordTypeTag) {
    List<AttestationPreimage.Fact> facts =
        AttestationPreimageFields.records(preimage, recordTypeTag);
    if (facts.size() != 1) {
      throw failure();
    }
    return facts.getFirst();
  }

  private static PlanChildOperation allowedChildOperation(
      AttestationPreimage.Fact command, AttestationAuthorizationFailure failure) {
    String operationToken = AttestationPreimageValueReader.token(command, 0, failure);
    return Arrays.stream(PlanChildOperation.values())
        .filter(operation -> operation.matches(operationToken))
        .findFirst()
        .orElseThrow(AttestationPlanChildMutationProfile::failure);
  }

  /** Closed direct-operation set that can appear as an execute-plan child. */
  private enum PlanChildOperation {
    /** A chart-of-accounts declaration, amendment, or reactivation. */
    DECLARE_ACCOUNT(AttestationOperationKind.DECLARE_ACCOUNT) {
      @Override
      void requireRequestEffectLinkage(
          AttestationPreimage childRequest, AttestationPreimage childEffect) {
        requireAccountLinkage(childRequest, childEffect);
      }
    },
    /** A tax-registration declaration or amendment. */
    DECLARE_TAX_REGISTRATION(AttestationOperationKind.DECLARE_TAX_REGISTRATION) {
      @Override
      void requireRequestEffectLinkage(
          AttestationPreimage childRequest, AttestationPreimage childEffect) {
        requireTaxRegistrationLinkage(childRequest, childEffect);
      }
    },
    /** A direct journal posting. */
    POST_ENTRY(AttestationOperationKind.POST_ENTRY) {
      @Override
      void requireRequestEffectLinkage(
          AttestationPreimage childRequest, AttestationPreimage childEffect) {
        requirePostingLinkage(childRequest, childEffect);
      }
    };

    private final AttestationOperationKind operationKind;

    PlanChildOperation(AttestationOperationKind operationKind) {
      this.operationKind = operationKind;
    }

    private AttestationOperationKind operationKind() {
      return operationKind;
    }

    private boolean matches(String operationToken) {
      return operationKind.wireToken().equals(operationToken);
    }

    abstract void requireRequestEffectLinkage(
        AttestationPreimage childRequest, AttestationPreimage childEffect);
  }

  private static AttestationAuthorizationException failure() {
    return AttestationOperationProfile.failure();
  }
}
