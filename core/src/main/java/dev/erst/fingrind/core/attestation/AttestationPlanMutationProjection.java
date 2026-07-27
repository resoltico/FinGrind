package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects ordered child mutations into the one immutable execute-plan operation payload. */
public final class AttestationPlanMutationProjection {
  private static final String EXECUTE_PLAN = AttestationOperationKind.EXECUTE_PLAN.wireToken();
  private static final String CLI = "cli";

  private AttestationPlanMutationProjection() {}

  /** Projects transaction-owned completed child mutations into one aggregate plan preimage. */
  public static AttestationOperationPreimages project(
      String planId, List<AttestationPlanChildMutation> childMutations) {
    String checkedPlanId = requirePlanId(planId);
    List<AttestationPlanChildMutation> checkedChildren =
        List.copyOf(Objects.requireNonNull(childMutations, "childMutations"));
    if (checkedChildren.isEmpty()) {
      throw new IllegalArgumentException(EXECUTE_PLAN + " requires at least one child mutation.");
    }
    requireStrictStepOrder(checkedChildren);
    List<AttestationPreimage.Fact> requestFacts = new ArrayList<>();
    requestFacts.add(command(checkedPlanId));
    List<AttestationPreimage.Fact> effectFacts = new ArrayList<>();
    for (AttestationPlanChildMutation child : checkedChildren) {
      appendQualifiedChildFacts(requestFacts, effectFacts, child);
    }
    AttestationPreimage request = AttestationPreimage.of(requestFacts);
    AttestationPreimage effect = AttestationPreimage.of(effectFacts);
    AttestationPlanQualifiedFact.requireValid(request, effect);
    return new AttestationOperationPreimages(request.encoded(), effect.encoded());
  }

  private static void appendQualifiedChildFacts(
      List<AttestationPreimage.Fact> requestFacts,
      List<AttestationPreimage.Fact> effectFacts,
      AttestationPlanChildMutation child) {
    AttestationPreimage request =
        AttestationPreimage.decode(
            child.preimages().request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage effect =
        AttestationPreimage.decode(
            child.preimages().effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    List<AttestationPreimage.Fact> commands = AttestationPreimageFields.records(request, 0x0100);
    if (commands.size() != 1
        || !child
            .operationKind()
            .equals(
                AttestationPreimageValueReader.token(
                    commands.getFirst(), 0, AttestationAuthorizationFailure.PREIMAGE_INVALID))) {
      throw new IllegalArgumentException(
          "A ledger-plan child mutation must retain its own matching command preimage.");
    }
    try {
      AttestationPlanChildMutationProfile.requireValid(request, effect);
    } catch (AttestationAuthorizationException exception) {
      throw new IllegalArgumentException(
          "A ledger-plan child mutation must retain a valid direct operation profile.", exception);
    }
    request.records().stream()
        .map(fact -> AttestationPlanQualifiedFact.requestFact(child.stepOrder(), fact))
        .forEach(requestFacts::add);
    effect.records().stream()
        .map(fact -> AttestationPlanQualifiedFact.effectFact(child.stepOrder(), fact))
        .forEach(effectFacts::add);
  }

  private static AttestationPreimage.Fact command(String planId) {
    AttestationField planReference =
        AttestationField.present(AttestationTextFieldValue.text(planId));
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(EXECUTE_PLAN)),
            planReference,
            planReference,
            AttestationField.present(AttestationTextFieldValue.token(CLI))));
  }

  private static void requireStrictStepOrder(List<AttestationPlanChildMutation> childMutations) {
    int previousStepOrder = -1;
    for (AttestationPlanChildMutation child : childMutations) {
      AttestationPlanChildMutation checkedChild = Objects.requireNonNull(child, "childMutations");
      if (checkedChild.stepOrder() <= previousStepOrder) {
        throw new IllegalArgumentException(
            EXECUTE_PLAN + " child mutations must have strictly increasing stepOrder values.");
      }
      previousStepOrder = checkedChild.stepOrder();
    }
  }

  private static String requirePlanId(String planId) {
    String checkedPlanId = Objects.requireNonNull(planId, "planId");
    if (checkedPlanId.isBlank()) {
      throw new IllegalArgumentException("planId must not be blank.");
    }
    return checkedPlanId;
  }
}
