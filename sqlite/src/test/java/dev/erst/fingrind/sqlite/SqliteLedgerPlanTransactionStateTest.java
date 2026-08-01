package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Protects the internal ledger-plan state machine independently of SQLite I/O. */
class SqliteLedgerPlanTransactionStateTest {
  private static final AttestationOperationPreimages CHILD_PREIMAGES =
      new AttestationOperationPreimages(new byte[] {1}, new byte[] {2});

  @Test
  void attestedPlanState_rejectsInvalidConstructionAndIllegalStepTransitions() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

    assertThrows(
        IllegalArgumentException.class,
        () -> new SqlitePlanAttestationState(" ", authorizer, -1, List.of(), false));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqlitePlanAttestationState("plan", authorizer, -2, List.of(), false));

    SqlitePlanAttestationState begun = SqlitePlanAttestationState.begun("plan", authorizer);
    assertThrows(IllegalArgumentException.class, () -> begun.enterStep(-1));

    SqlitePlanAttestationState firstStep = begun.enterStep(0);
    assertThrows(IllegalStateException.class, () -> firstStep.enterStep(0));
  }

  @Test
  void attestedPlanState_tracksOneChildPerStepAndOneAggregateAppend() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
    SqlitePlanAttestationState begun = SqlitePlanAttestationState.begun("plan", authorizer);

    assertThrows(IllegalStateException.class, () -> begun.requireChildMayComplete(authorizer));
    assertThrows(IllegalArgumentException.class, () -> begun.appendAggregate(authorizer));

    SqlitePlanAttestationState completed =
        begun.enterStep(0).completeChild(authorizer, "record-posting", CHILD_PREIMAGES);
    assertEquals(1, completed.completedChildren().size());
    assertThrows(
        IllegalStateException.class,
        () -> completed.completeChild(authorizer, "record-posting", CHILD_PREIMAGES));

    SqlitePlanAttestationState appended = completed.appendAggregate(authorizer);
    assertTrue(appended.aggregateAppended());
    assertThrows(IllegalStateException.class, () -> appended.enterStep(1));
    assertThrows(
        IllegalStateException.class,
        () -> appended.completeChild(authorizer, "record-posting", CHILD_PREIMAGES));
    assertThrows(IllegalStateException.class, () -> appended.appendAggregate(authorizer));
  }

  @Test
  void attestedPlanState_keepsTheExactBoundAuthorizer() {
    AttestationPlanOperationAuthorizer owner = planAuthorizer();
    AttestationPlanOperationAuthorizer stranger = planAuthorizer();
    SqlitePlanAttestationState state = SqlitePlanAttestationState.begun("plan", owner).enterStep(0);

    assertThrows(IllegalArgumentException.class, () -> state.requireSameAuthorizer(stranger));
    assertThrows(
        IllegalArgumentException.class,
        () -> state.completeChild(stranger, "record-posting", CHILD_PREIMAGES));
    assertThrows(IllegalArgumentException.class, () -> state.appendAggregate(stranger));
  }

  @Test
  void readOnlyPlanState_rejectsInvalidPlanAndNonMonotonicSteps() {
    assertThrows(IllegalArgumentException.class, () -> new SqliteReadOnlyPlanState(" ", -1));
    assertThrows(IllegalArgumentException.class, () -> new SqliteReadOnlyPlanState("plan", -2));

    SqliteReadOnlyPlanState begun = SqliteReadOnlyPlanState.begun("read-only-plan");
    assertThrows(IllegalArgumentException.class, () -> begun.enterStep(-1));
    SqliteReadOnlyPlanState firstStep = begun.enterStep(0);
    assertThrows(IllegalStateException.class, () -> firstStep.enterStep(0));
  }

  @Test
  void activeTransaction_preservesDatabaseAndPlanStateBoundaries() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
    ActiveLedgerPlanTransaction attested =
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(),
            SqlitePlanAttestationState.begun("plan", authorizer),
            NullTestSupport.nullOf(SqliteAttestationEvidenceStore.ObservedHead.class));
    assertFalse(attested.begunInDatabase());
    assertTrue(attested.attestedPlan());
    assertFalse(attested.readOnlyPlan());

    ActiveLedgerPlanTransaction readOnly =
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(),
            SqliteReadOnlyPlanState.begun("read-only-plan"),
            NullTestSupport.nullOf(SqliteAttestationEvidenceStore.ObservedHead.class));
    assertTrue(readOnly.readOnlyPlan());
    assertThrows(IllegalStateException.class, readOnly::requireAttestationState);
    assertTrue(
        readOnly
            .withBegunDatabase(
                NullTestSupport.nullOf(SqliteAttestationEvidenceStore.ObservedHead.class))
            .begunInDatabase());
  }

  @Test
  void execution_distinguishesInactiveReadOnlyAndAggregateAttestedPlans() {
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();
    SqliteLedgerPlanTransactionStateHolder holder = new SqliteLedgerPlanTransactionStateHolder();
    SqliteLedgerPlanExecution execution = new SqliteLedgerPlanExecution(holder);

    assertFalse(execution.activeAttestedPlan());
    assertFalse(execution.hasCompletedPlanChildren());
    assertThrows(IllegalStateException.class, execution::requireObservedAttestationHead);
    assertDoesNotThrow(execution::requireDirectMutationPermitted);

    holder.replace(
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(),
            SqliteReadOnlyPlanState.begun("read-only-plan"),
            NullTestSupport.nullOf(SqliteAttestationEvidenceStore.ObservedHead.class)));
    assertFalse(execution.activeAttestedPlan());
    assertFalse(execution.hasCompletedPlanChildren());
    assertThrows(IllegalStateException.class, execution::requireObservedAttestationHead);

    holder.replace(
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(),
            SqlitePlanAttestationState.begun("attested-plan", authorizer),
            NullTestSupport.nullOf(SqliteAttestationEvidenceStore.ObservedHead.class)));
    assertTrue(execution.activeAttestedPlan());
    assertFalse(execution.hasCompletedPlanChildren());
    assertThrows(IllegalStateException.class, execution::requireObservedAttestationHead);
    assertThrows(IllegalArgumentException.class, () -> execution.aggregatePreimages(authorizer));

    execution.enterPlanStep(0);
    execution.recordCompletedPlanChild(authorizer, "record-posting", CHILD_PREIMAGES);
    assertTrue(execution.hasCompletedPlanChildren());
    execution.markAggregateAppended(authorizer);
    assertThrows(IllegalStateException.class, () -> execution.aggregatePreimages(authorizer));
  }

  private static AttestationPlanOperationAuthorizer planAuthorizer() {
    return new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
  }
}
