package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.workflow.BookWorkflowExecutionResult;
import dev.erst.fingrind.executor.workflow.BookWorkflowExecutionService;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import java.time.Clock;
import java.util.Objects;

/** Executes canonical AI-agent ledger plans against one atomic book session. */
public final class LedgerPlanService {
  private final BookWorkflowExecutionService workflowExecutionService;

  /** Creates a ledger-plan executor. */
  public LedgerPlanService(
      LedgerPlanExecutionStore executionStore, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.workflowExecutionService =
        new BookWorkflowExecutionService(
            Objects.requireNonNull(executionStore, "executionStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            Objects.requireNonNull(clock, "clock"));
  }

  /** Executes one signed plan atomically, committing only when every step succeeds. */
  public LedgerPlanResult execute(
      LedgerPlan plan, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(plan, "plan");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    return publishedResult(
        workflowExecutionService.execute(
            BookWorkflowPublishedLanguageTranslator.fromPublished(plan), attestationAuthorizer));
  }

  private static LedgerPlanResult publishedResult(BookWorkflowExecutionResult executionResult) {
    LedgerPlanId publishedPlanId =
        BookWorkflowPublishedLanguageTranslator.toPublishedPlanId(executionResult.planId());
    var publishedJournal =
        BookWorkflowPublishedLanguageTranslator.toPublished(executionResult.journal());
    return switch (executionResult.status()) {
      case SUCCEEDED ->
          new LedgerPlanResult.Succeeded(
              publishedPlanId,
              publishedJournal,
              executionResult.attestationCommit() == null
                  ? LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION
                  : LedgerPlanAttestationDisposition.APPENDED,
              executionResult.attestationCommit());
      case REJECTED -> new LedgerPlanResult.Rejected(publishedPlanId, publishedJournal);
      case ASSERTION_FAILED ->
          new LedgerPlanResult.AssertionFailed(publishedPlanId, publishedJournal);
    };
  }
}
