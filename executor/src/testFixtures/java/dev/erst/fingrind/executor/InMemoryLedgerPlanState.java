package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanChildMutation;
import dev.erst.fingrind.core.attestation.AttestationPlanMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Owns the transactional aggregate-attestation state of one in-memory ledger-plan fixture. */
final class InMemoryLedgerPlanState {
  private static final AttestationCommit PLAN_COMMIT =
      new AttestationCommit(BigInteger.ONE, "f".repeat(64));

  private @Nullable InMemoryBookSessionSnapshot transactionSnapshot;
  private @Nullable Map<PostingId, AttestationCommit> commitmentSnapshot;
  private @Nullable String activePlanId;
  private @Nullable AttestationPlanOperationAuthorizer activePlanAuthorizer;
  private boolean activeReadOnlyPlan;
  private int activePlanStepOrder = -1;
  private boolean aggregateAppended;
  private final List<AttestationPlanChildMutation> completedChildren = new java.util.ArrayList<>();
  private final Map<PostingId, AttestationCommit> commitments =
      InMemoryBookSessionSupport.mutableMap();
  private final Set<PostingId> activePostingIds = new LinkedHashSet<>();

  void begin(
      String planId,
      @Nullable AttestationPlanOperationAuthorizer authorizer,
      boolean readOnly,
      InMemoryBookSessionSnapshot snapshot) {
    if (transactionSnapshot != null) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    String checkedPlanId = Objects.requireNonNull(planId, "planId");
    if (checkedPlanId.isBlank()) {
      throw new IllegalArgumentException("planId must not be blank.");
    }
    if (!readOnly) {
      Objects.requireNonNull(authorizer, "attestationAuthorizer");
    } else if (authorizer != null) {
      throw new IllegalArgumentException(
          "A read-only ledger plan must not retain an aggregate attestation authorizer.");
    }
    transactionSnapshot = Objects.requireNonNull(snapshot, "snapshot");
    commitmentSnapshot = Map.copyOf(commitments);
    activePlanId = checkedPlanId;
    activePlanAuthorizer = authorizer;
    activeReadOnlyPlan = readOnly;
    activePlanStepOrder = -1;
    aggregateAppended = false;
    completedChildren.clear();
    activePostingIds.clear();
  }

  void enterStep(int stepOrder) {
    requireActivePlan();
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan cannot execute child steps after its aggregate attestation append.");
    }
    if (stepOrder < 0) {
      throw new IllegalArgumentException("stepOrder must not be negative.");
    }
    if (stepOrder <= activePlanStepOrder) {
      throw new IllegalStateException("Ledger-plan steps must execute in strict source order.");
    }
    activePlanStepOrder = stepOrder;
  }

  boolean hasCompletedChildren() {
    return !completedChildren.isEmpty();
  }

  AttestationCommit appendAggregate(
      Instant recordedAt, AttestationPlanOperationAuthorizer authorizer) {
    Objects.requireNonNull(recordedAt, "recordedAt");
    requireActivePlanAuthorizer(authorizer);
    if (completedChildren.isEmpty()) {
      throw new IllegalArgumentException(
          "A ledger plan without completed child mutations must not request an aggregate attestation append.");
    }
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan may append its aggregate attestation exactly once.");
    }
    AttestationPlanMutationProjection.project(
        Objects.requireNonNull(activePlanId, "activePlanId"), completedChildren);
    activePostingIds.forEach(postingId -> commitments.put(postingId, PLAN_COMMIT));
    aggregateAppended = true;
    return PLAN_COMMIT;
  }

  void requireChildMutation(AttestationPlanOperationAuthorizer authorizer) {
    requireActivePlanAuthorizer(authorizer);
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan cannot record child mutations after its aggregate attestation append.");
    }
    if (activePlanStepOrder < 0) {
      throw new IllegalStateException(
          "A ledger-plan child mutation requires an active source-plan step.");
    }
    if (!completedChildren.isEmpty()
        && completedChildren.getLast().stepOrder() >= activePlanStepOrder) {
      throw new IllegalStateException(
          "A ledger-plan step may complete at most one attested child mutation.");
    }
  }

  void recordCompletedChild(
      String operationKind,
      AttestationOperationPreimages preimages,
      @Nullable PostingId deferredPostingId) {
    completedChildren.add(
        new AttestationPlanChildMutation(activePlanStepOrder, operationKind, preimages));
    if (deferredPostingId != null) {
      activePostingIds.add(deferredPostingId);
    }
  }

  Map<PostingId, AttestationCommit> commitmentsFor(Set<PostingId> postingIds) {
    return Objects.requireNonNull(postingIds, "postingIds").stream()
        .filter(commitments::containsKey)
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                postingId -> postingId, commitments::get));
  }

  void commit(Consumer<InMemoryBookSessionSnapshot> restoreSnapshot) {
    requireActivePlan();
    if (!completedChildren.isEmpty() && !aggregateAppended) {
      rollback(restoreSnapshot);
      throw new IllegalStateException(
          "A ledger plan with completed child mutations must append its aggregate attestation before commit.");
    }
    clear();
  }

  void rollback(Consumer<InMemoryBookSessionSnapshot> restoreSnapshot) {
    Objects.requireNonNull(restoreSnapshot, "restoreSnapshot");
    if (transactionSnapshot == null) {
      return;
    }
    restoreSnapshot.accept(transactionSnapshot);
    commitments.clear();
    commitments.putAll(Objects.requireNonNull(commitmentSnapshot, "commitmentSnapshot"));
    clear();
  }

  void requireDirectMutationPermitted() {
    if (transactionSnapshot != null) {
      throw new IllegalStateException(
          "Direct attested mutations cannot run inside an aggregate-attested ledger plan.");
    }
  }

  private void requireActivePlanAuthorizer(AttestationPlanOperationAuthorizer authorizer) {
    requireActivePlan();
    if (activeReadOnlyPlan) {
      throw new IllegalStateException(
          "A read-only ledger plan cannot authorize attested child mutations or aggregate appends.");
    }
    if (!Objects.equals(activePlanAuthorizer, Objects.requireNonNull(authorizer, "authorizer"))) {
      throw new IllegalArgumentException(
          "The supplied aggregate plan authority does not own this ledger-plan transaction.");
    }
  }

  private void requireActivePlan() {
    if (transactionSnapshot == null) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
  }

  private void clear() {
    transactionSnapshot = null;
    commitmentSnapshot = null;
    activePlanId = null;
    activePlanAuthorizer = null;
    activeReadOnlyPlan = false;
    activePlanStepOrder = -1;
    aggregateAppended = false;
    completedChildren.clear();
    activePostingIds.clear();
  }
}
