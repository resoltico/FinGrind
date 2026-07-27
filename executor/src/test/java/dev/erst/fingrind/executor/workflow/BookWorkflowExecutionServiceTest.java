package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.CLOCK;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.EXECUTED_AT;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.POSTING_ID;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.ExecutorAccountingTestSupport;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies mutable workflow boundaries preserve deterministic contract failures unchanged. */
class BookWorkflowExecutionServiceTest {
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "beginLedgerPlanTransaction",
        "enterLedgerPlanStep",
        "commitLedgerPlanTransaction"
      })
  void executionService_preservesDeterministicContractFailuresFromEveryTransactionPhase(
      String phase) {
    ContractFailureException expected =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));
    BookWorkflowExecutionService service =
        new BookWorkflowExecutionService(faultingStore(phase, expected), () -> POSTING_ID, CLOCK);

    ContractFailureException actual =
        assertThrows(
            ContractFailureException.class,
            () ->
                service.execute(
                    new BookWorkflowPlan(
                        new BookWorkflowPlanId("contract-" + phase),
                        List.of(new BookWorkflowStep.InspectBook(stepId("inspect")))),
                    TEST_AUTHORIZER));

    assertSame(expected, actual);
  }

  @Test
  void mutableStepExecutor_exposesTheCurrentBookInspection() {
    LedgerPlanExecutionStore executionStore =
        faultingStore("none", new IllegalStateException("The store must not fail."));
    LedgerPlanStepExecutor executor =
        new LedgerPlanStepExecutor(executionStore, () -> POSTING_ID, CLOCK);

    BookLifecycleInspection inspection = executor.inspectBook();

    assertEquals(BookLifecycleInspection.Status.INITIALIZED, inspection.status());
  }

  private static LedgerPlanExecutionStore faultingStore(
      String failureMethod, RuntimeException failure) {
    return (LedgerPlanExecutionStore)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {LedgerPlanExecutionStore.class},
            (proxy, method, arguments) -> {
              String methodName = method.getName();
              if (failureMethod.equals(methodName)) {
                throw failure;
              }
              return switch (methodName) {
                case "inspectBook" ->
                    ExecutorAccountingTestSupport.initializedLifecycleInspection(
                        1001, 52, 52, EXECUTED_AT);
                case "allowsInitializedWorkflow" -> true;
                case "beginLedgerPlanTransaction",
                    "enterLedgerPlanStep",
                    "commitLedgerPlanTransaction",
                    "rollbackLedgerPlanTransaction" ->
                    null;
                case "hasCompletedLedgerPlanChildren" -> false;
                default ->
                    throw new AssertionError("Unexpected mutable workflow call: " + methodName);
              };
            });
  }
}
