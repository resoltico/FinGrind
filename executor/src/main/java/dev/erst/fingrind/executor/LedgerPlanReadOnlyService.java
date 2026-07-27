package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;
import dev.erst.fingrind.executor.workflow.BookWorkflowExecutionResult;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowReadOnlyExecutionService;
import java.time.Clock;
import java.util.Objects;

/** Executes canonical credential-free ledger plans through a read-only protected-book session. */
public final class LedgerPlanReadOnlyService {
  private final BookWorkflowReadOnlyExecutionService workflowExecutionService;

  /** Creates one read-only ledger-plan executor. */
  public LedgerPlanReadOnlyService(LedgerPlanReadOnlyExecutionStore executionStore, Clock clock) {
    workflowExecutionService =
        new BookWorkflowReadOnlyExecutionService(
            Objects.requireNonNull(executionStore, "executionStore"),
            Objects.requireNonNull(clock, "clock"));
  }

  /** Executes one credential-free plan that may inspect, preflight, query, or assert. */
  public LedgerPlanResult execute(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    var workflowPlan = BookWorkflowPublishedLanguageTranslator.fromPublished(plan);
    if (plan.containsBookMutation()) {
      return publishedResult(workflowExecutionService.rejectMutationPlan(workflowPlan));
    }
    return publishedResult(workflowExecutionService.execute(workflowPlan));
  }

  private static LedgerPlanResult publishedResult(BookWorkflowExecutionResult executionResult) {
    LedgerPlanId publishedPlanId =
        BookWorkflowPublishedLanguageTranslator.toPublishedPlanId(executionResult.planId());
    var publishedJournal =
        BookWorkflowPublishedLanguageTranslator.toPublished(executionResult.journal());
    return switch (executionResult.status()) {
      case SUCCEEDED ->
          new LedgerPlanResult.Succeeded(
              publishedPlanId, publishedJournal, LedgerPlanAttestationDisposition.READ_ONLY, null);
      case REJECTED -> new LedgerPlanResult.Rejected(publishedPlanId, publishedJournal);
      case ASSERTION_FAILED ->
          new LedgerPlanResult.AssertionFailed(publishedPlanId, publishedJournal);
    };
  }
}
