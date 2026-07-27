package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.CLOCK;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.EXECUTED_AT;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountDeclaration;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.initializedBook;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.ExecutorAccountingTestSupport;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises read-only workflow transaction boundaries with real plans and controlled store faults.
 */
class BookWorkflowReadOnlyExecutionServiceTest {
  private static final MethodHandle REJECTED_MUTATION_PLAN = rejectedMutationPlan();

  @Test
  void execute_commitsReadOnlyPlansAndRetainsEverySuccessfulStep() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookWorkflowExecutionResult result =
          new BookWorkflowReadOnlyExecutionService(bookSession, CLOCK)
              .execute(
                  plan(
                      "read-plan",
                      new BookWorkflowStep.InspectBook(stepId("inspect")),
                      new BookWorkflowStep.ListAccounts(stepId("accounts"), accountQuery())));

      assertEquals(BookWorkflowExecutionStatus.SUCCEEDED, result.status());
      assertEquals(2, result.journal().entries().size());
      assertEquals("inspect", result.journal().entries().getFirst().stepId().value());
      assertEquals("accounts", result.journal().entries().getLast().stepId().value());
    }
  }

  @Test
  void execute_rejectsMutatingPlansAndReportsTheFirstMutation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookWorkflowExecutionResult result =
          new BookWorkflowReadOnlyExecutionService(bookSession, CLOCK)
              .execute(
                  plan(
                      "mutation-plan",
                      new BookWorkflowStep.InspectBook(stepId("inspect")),
                      new BookWorkflowStep.DeclareAccount(
                          stepId("declare"), accountDeclaration())));

      assertEquals(BookWorkflowExecutionStatus.REJECTED, result.status());
      assertEquals("declare", result.journal().terminalEntry().stepId().value());
      assertEquals(
          "read-only-plan-mutation-forbidden",
          result.journal().requiredFailedEntry().requiredFailure().code());
    }
  }

  @Test
  void execute_reportsMissingBooksAndAssertionFailuresAsTerminalPlanResults() {
    try (InMemoryBookSession missingBook = new InMemoryBookSession()) {
      BookWorkflowExecutionResult missingResult =
          new BookWorkflowReadOnlyExecutionService(missingBook, CLOCK)
              .execute(plan("missing", new BookWorkflowStep.InspectBook(stepId("inspect"))));

      assertEquals(BookWorkflowExecutionStatus.REJECTED, missingResult.status());
      assertEquals(
          "query-book-not-initialized",
          missingResult.journal().requiredFailedEntry().requiredFailure().code());
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookWorkflowExecutionResult assertionResult =
          new BookWorkflowReadOnlyExecutionService(bookSession, CLOCK)
              .execute(
                  plan(
                      "assertion",
                      new BookWorkflowAssertionStep(
                          stepId("account-exists"),
                          new BookWorkflowAssertion.AccountDeclared(new AccountCode("1000")))));

      assertEquals(BookWorkflowExecutionStatus.ASSERTION_FAILED, assertionResult.status());
      assertEquals(
          "assertion-failed",
          assertionResult.journal().requiredFailedEntry().requiredFailure().code());
    }
  }

  @Test
  void rejectMutationPlan_refusesPlansThatAreAlreadyReadOnly() {
    BookWorkflowReadOnlyExecutionService service =
        new BookWorkflowReadOnlyExecutionService(
            faultingStore("none", new IllegalStateException("The store must not be reached.")),
            CLOCK);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.rejectMutationPlan(
                    plan("inspect", new BookWorkflowStep.InspectBook(stepId("inspect")))));

    assertEquals(
        "Only a plan containing a book mutation may be rejected here.",
        Objects.requireNonNull(exception.getMessage()));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "beginReadOnlyLedgerPlanTransaction",
        "allowsInitializedWorkflow",
        "inspectBook",
        "enterLedgerPlanStep",
        "commitLedgerPlanTransaction"
      })
  void readOnlyWorkflow_preservesDeterministicContractFailuresFromEveryTransactionPhase(
      String phase) {
    ContractFailureException expected =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));
    BookWorkflowReadOnlyExecutionService service =
        new BookWorkflowReadOnlyExecutionService(faultingStore(phase, expected), CLOCK);

    ContractFailureException actual =
        assertThrows(
            ContractFailureException.class,
            () ->
                service.execute(
                    plan(
                        "contract-" + phase, new BookWorkflowStep.InspectBook(stepId("inspect")))));

    assertSame(expected, actual);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "beginReadOnlyLedgerPlanTransaction",
        "allowsInitializedWorkflow",
        "inspectBook",
        "enterLedgerPlanStep",
        "commitLedgerPlanTransaction"
      })
  void readOnlyWorkflow_recordsUnexpectedFailuresAtEveryTransactionBoundary(String phase) {
    BookWorkflowReadOnlyExecutionService service =
        new BookWorkflowReadOnlyExecutionService(
            faultingStore(phase, new IllegalStateException(phase + " failed")), CLOCK);

    BookWorkflowExecutionResult result =
        service.execute(
            plan("runtime-" + phase, new BookWorkflowStep.InspectBook(stepId("inspect"))));

    assertEquals(BookWorkflowExecutionStatus.REJECTED, result.status());
    assertEquals(
        ("inspectBook".equals(phase) || "enterLedgerPlanStep".equals(phase))
            ? "unexpected-step-failure"
            : "unexpected-plan-failure",
        result.journal().requiredFailedEntry().requiredFailure().code());
  }

  @Test
  void rejectedMutationPlan_defensivelyRejectsAnImpossibleNonMutationInvocation() {
    BookWorkflowReadOnlyExecutionService service =
        new BookWorkflowReadOnlyExecutionService(
            faultingStore("none", new IllegalStateException("The store must not be reached.")),
            CLOCK);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokeRejectedMutationPlan(
                    service, plan("inspect", new BookWorkflowStep.InspectBook(stepId("inspect")))));

    assertEquals(
        "A mutation-rejected plan must contain a mutation step.",
        Objects.requireNonNull(exception.getMessage()));
  }

  private static BookWorkflowPlan plan(String planId, BookWorkflowStep... steps) {
    return new BookWorkflowPlan(new BookWorkflowPlanId(planId), List.of(steps));
  }

  private static LedgerPlanReadOnlyExecutionStore faultingStore(
      String failureMethod, RuntimeException failure) {
    return (LedgerPlanReadOnlyExecutionStore)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {LedgerPlanReadOnlyExecutionStore.class},
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
                case "beginReadOnlyLedgerPlanTransaction",
                    "enterLedgerPlanStep",
                    "commitLedgerPlanTransaction",
                    "rollbackLedgerPlanTransaction" ->
                    null;
                default ->
                    throw new AssertionError("Unexpected read-only workflow call: " + methodName);
              };
            });
  }

  private static MethodHandle rejectedMutationPlan() {
    try {
      return MethodHandles.privateLookupIn(
              BookWorkflowReadOnlyExecutionService.class, MethodHandles.lookup())
          .findVirtual(
              BookWorkflowReadOnlyExecutionService.class,
              "rejectedMutationPlan",
              MethodType.methodType(
                  BookWorkflowExecutionResult.class, BookWorkflowPlan.class, Instant.class));
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static BookWorkflowExecutionResult invokeRejectedMutationPlan(
      BookWorkflowReadOnlyExecutionService service, BookWorkflowPlan plan) {
    try {
      return (BookWorkflowExecutionResult)
          REJECTED_MUTATION_PLAN.invoke(service, plan, EXECUTED_AT);
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new AssertionError(throwable);
    }
  }
}
