package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.groupFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.inspectBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.postEntryCommand;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for rejected ledger-plan execution and rollback-boundary behavior. */
class LedgerPlanServiceFailureWorkflowTest {
  @Test
  void execute_rollsBackAndJournalsUnexpectedRuntimeFailures() {
    try (LedgerPlanServiceTestSupport.ThrowingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.ThrowingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      new LedgerStep.ListAccounts(
                          stepId("accounts"), new ListAccountsQuery(1, Optional.empty())))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-step-failure", result.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("step 'accounts': boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact -> textFact(fact, "exceptionType", IllegalStateException.class.getName())));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_preservesPriorSuccessfulStepsBeforeUnexpectedRuntimeFailure() {
    try (LedgerPlanServiceTestSupport.DeclareRuntimeFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareRuntimeFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      inspectBookStep("open"),
                      new LedgerStep.DeclareAccount(
                          stepId("cash"),
                          account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(2, result.journal().steps().size());
      assertEquals("open", result.journal().steps().get(0).stepId().value());
      assertEquals(LedgerStepStatus.SUCCEEDED, result.journal().steps().get(0).status());
      assertEquals("cash", result.journal().steps().get(1).stepId().value());
      assertEquals(
          "unexpected-step-failure", result.journal().steps().getLast().requiredFailure().code());
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_rethrowsStaleHeadAndRollsBackWhenAChildWriteLosesAdmission() {
    try (LedgerPlanServiceTestSupport.DeclareStaleHeadLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareStaleHeadLedgerPlanSession()) {
      AttestationStaleHeadException staleHead =
          assertThrows(
              AttestationStaleHeadException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-stale-child"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.staleHead(), staleHead);
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_rethrowsStaleHeadAndRollsBackWhenAggregateAdmissionLosesTheHead() {
    try (LedgerPlanServiceTestSupport.AggregateStaleHeadLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.AggregateStaleHeadLedgerPlanSession()) {
      AttestationStaleHeadException staleHead =
          assertThrows(
              AttestationStaleHeadException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-stale-aggregate"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.staleHead(), staleHead);
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_rethrowsAdmissionRejectionAndRollsBackWhenAChildWriteIsUnauthorized() {
    try (LedgerPlanServiceTestSupport.DeclareAdmissionRejectedLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareAdmissionRejectedLedgerPlanSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-admission-child"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.admissionRejected(), rejected);
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_rethrowsAdmissionRejectionAndRollsBackWhenAggregateAdmissionIsUnauthorized() {
    try (LedgerPlanServiceTestSupport.AggregateAdmissionRejectedLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.AggregateAdmissionRejectedLedgerPlanSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-admission-aggregate"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.admissionRejected(), rejected);
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_preservesAdmissionRejectionWhenRollbackAlsoFails() {
    try (LedgerPlanServiceTestSupport.DeclareAdmissionRejectedLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareAdmissionRejectedLedgerPlanSession(true)) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-admission-rollback"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.admissionRejected(), rejected);
      assertTrue(bookSession.rollbackCalled());
      assertEquals(1, rejected.getSuppressed().length);
      assertEquals("rollback boom", rejected.getSuppressed()[0].getMessage());
    }
  }

  @Test
  void execute_preservesStaleHeadWhenRollbackAlsoFails() {
    try (LedgerPlanServiceTestSupport.DeclareStaleHeadLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareStaleHeadLedgerPlanSession(true)) {
      AttestationStaleHeadException staleHead =
          assertThrows(
              AttestationStaleHeadException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("plan-stale-rollback"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(bookSession.staleHead(), staleHead);
      assertTrue(bookSession.rollbackCalled());
      assertEquals(1, staleHead.getSuppressed().length);
      assertEquals("rollback boom", staleHead.getSuppressed()[0].getMessage());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenTransactionBeginFails() {
    try (LedgerPlanServiceTestSupport.BeginFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.BeginFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(planId("plan-1"), List.of(inspectBookStep("open"))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(
          LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryCheckpoint.BEGIN, result.journal().steps().getLast().boundaryCheckpoint());
      assertTrue(
          result.journal().steps().getLast().requiredFailure().message().contains("during begin"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "checkpoint", "begin")));
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenInitializationCheckThrows() {
    try (LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      new LedgerStep.DeclareAccount(
                          stepId("cash"),
                          account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(
          LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryCheckpoint.INITIALIZATION_CHECK,
          result.journal().steps().getLast().boundaryCheckpoint());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during initialization-check before step 'cash': initialization boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "checkpoint", "initialization-check")));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_rethrowsCanonicalContractFailureAndRollsBackExactlyOnce() {
    ContractFailureException expected =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));
    try (LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession(expected)) {
      ContractFailureException actual =
          assertThrows(
              ContractFailureException.class,
              () ->
                  service(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("mutating-format"),
                              List.of(
                                  new LedgerStep.DeclareAccount(
                                      stepId("cash"),
                                      account(
                                          "1000",
                                          "Cash",
                                          AccountType.ASSET,
                                          NormalBalance.DEBIT)))),
                          ExecutorAccountingTestSupport.TEST_AUTHORIZER));

      assertSame(expected, actual);
      assertTrue(bookSession.rollbackCalled());
      assertEquals(1, bookSession.rollbackCalls());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenCommitFailsAfterSuccessfulSteps() {
    try (LedgerPlanServiceTestSupport.CommitFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.CommitFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(planId("plan-1"), List.of(inspectBookStep("open"))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(
          LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryCheckpoint.COMMIT, result.journal().steps().getLast().boundaryCheckpoint());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during commit after step 'open': commit boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "checkpoint", "commit")));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenRollbackFailsAfterDeterministicStepFailure() {
    try (LedgerPlanServiceTestSupport.RollbackFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.RollbackFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(
          LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryCheckpoint.ROLLBACK,
          result.journal().steps().getLast().boundaryCheckpoint());
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "checkpoint", "rollback")));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact ->
                      fact instanceof dev.erst.fingrind.contract.workflow.LedgerFact.Group group
                          && "priorFailure".equals(group.name())
                          && group.facts().stream()
                              .anyMatch(
                                  child -> textFact(child, "code", "account-state-violations"))));
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenRollbackFailsAfterUnexpectedStepFailure() {
    try (LedgerPlanServiceTestSupport.RuntimeRollbackFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.RuntimeRollbackFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      new LedgerStep.ListAccounts(
                          stepId("accounts"), new ListAccountsQuery(1, Optional.empty())))),
              ExecutorAccountingTestSupport.TEST_AUTHORIZER);

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(
          LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryCheckpoint.ROLLBACK,
          result.journal().steps().getLast().boundaryCheckpoint());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during rollback after step 'accounts': rollback boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "checkpoint", "rollback")));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact,
                          "priorFailure",
                          "code",
                          "unexpected-step-failure",
                          "message",
                          "Ledger plan execution failed unexpectedly during step 'accounts': boom")));
    }
  }
}
