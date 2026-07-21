package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Projects ordered child mutations into the one immutable execute-plan operation payload. */
final class AttestationPlanMutationProjection {
  private static final String EXECUTE_PLAN = AttestationOperationKind.EXECUTE_PLAN.wireToken();
  private static final String CLI = "cli";
  private static final Set<Integer> REQUEST_STEP_ORDER_TAGS =
      Set.of(
          0x0120, 0x0121, 0x0122, 0x0123, 0x0124, 0x0126, 0x0127, 0x0128, 0x0129, 0x012A, 0x0130,
          0x0131, 0x0132, 0x0133, 0x0134);

  private AttestationPlanMutationProjection() {}

  static AttestationOperationPreimages project(
      String planId, List<AttestationPlanOperationAuthorizer.ChildMutation> childMutations) {
    String checkedPlanId = requirePlanId(planId);
    List<AttestationPlanOperationAuthorizer.ChildMutation> checkedChildren =
        List.copyOf(Objects.requireNonNull(childMutations, "childMutations"));
    if (checkedChildren.isEmpty()) {
      throw new IllegalArgumentException(EXECUTE_PLAN + " requires at least one child mutation.");
    }
    requireStrictStepOrder(checkedChildren);
    List<AttestationPreimage.Fact> requestFacts = new ArrayList<>();
    requestFacts.add(command(checkedPlanId));
    List<AttestationPreimage.Fact> effectFacts = new ArrayList<>();
    for (AttestationPlanOperationAuthorizer.ChildMutation child : checkedChildren) {
      appendChildRequestFacts(requestFacts, child);
      appendChildFacts(effectFacts, child.preimages().effect(), child.stepOrder(), false);
    }
    return new AttestationOperationPreimages(
        AttestationPreimage.of(requestFacts).encoded(),
        AttestationPreimage.of(effectFacts).encoded());
  }

  private static void appendChildRequestFacts(
      List<AttestationPreimage.Fact> target,
      AttestationPlanOperationAuthorizer.ChildMutation child) {
    AttestationPreimage decoded =
        AttestationPreimage.decode(
            child.preimages().request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    List<AttestationPreimage.Fact> commands = AttestationPreimageFields.records(decoded, 0x0100);
    if (commands.size() != 1
        || !child
            .operationKind()
            .equals(
                AttestationPreimageValueReader.token(
                    commands.getFirst(), 0, AttestationAuthorizationFailure.PREIMAGE_INVALID))) {
      throw new IllegalArgumentException(
          "A ledger-plan child mutation must retain its own matching command preimage.");
    }
    for (AttestationPreimage.Fact fact : decoded.records()) {
      if (fact.recordTypeTag() != 0x0100) {
        target.add(rewriteStepOrder(fact, child.stepOrder(), true));
      }
    }
  }

  private static void appendChildFacts(
      List<AttestationPreimage.Fact> target, byte[] encoded, int stepOrder, boolean request) {
    AttestationPreimage decoded =
        AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
    for (AttestationPreimage.Fact fact : decoded.records()) {
      if (request && fact.recordTypeTag() == 0x0100) {
        continue;
      }
      target.add(rewriteStepOrder(fact, stepOrder, request));
    }
  }

  private static AttestationPreimage.Fact rewriteStepOrder(
      AttestationPreimage.Fact fact, int stepOrder, boolean request) {
    int stepOrderField =
        request && REQUEST_STEP_ORDER_TAGS.contains(fact.recordTypeTag())
            ? 0
            : !request && fact.recordTypeTag() == 0x0020 ? 2 : -1;
    if (stepOrderField < 0) {
      return fact;
    }
    List<AttestationField> fields = new ArrayList<>(fact.fields());
    fields.set(
        stepOrderField,
        AttestationField.present(
            AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(stepOrder))));
    return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
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

  private static void requireStrictStepOrder(
      List<AttestationPlanOperationAuthorizer.ChildMutation> childMutations) {
    int previousStepOrder = -1;
    for (AttestationPlanOperationAuthorizer.ChildMutation child : childMutations) {
      AttestationPlanOperationAuthorizer.ChildMutation checkedChild =
          Objects.requireNonNull(child, "childMutations");
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
