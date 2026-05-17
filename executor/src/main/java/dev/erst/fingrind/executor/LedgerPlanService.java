package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
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
      LedgerPlanTransaction transactionStore,
      BookAdministrationStore administrationStore,
      AccountCatalogStore accountCatalogStore,
      BookkeepingReadStore readStore,
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.workflowExecutionService =
        new BookWorkflowExecutionService(
            Objects.requireNonNull(transactionStore, "transactionStore"),
            Objects.requireNonNull(administrationStore, "administrationStore"),
            Objects.requireNonNull(accountCatalogStore, "accountCatalogStore"),
            Objects.requireNonNull(readStore, "readStore"),
            Objects.requireNonNull(validationStore, "validationStore"),
            Objects.requireNonNull(commitStore, "commitStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            Objects.requireNonNull(clock, "clock"));
  }

  /** Executes one plan atomically, committing only when every step succeeds. */
  public LedgerPlanResult execute(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    return publishedResult(
        workflowExecutionService.execute(
            BookWorkflowPublishedLanguageTranslator.fromPublished(plan)));
  }

  private static LedgerPlanResult publishedResult(BookWorkflowExecutionResult executionResult) {
    LedgerPlanId publishedPlanId =
        BookWorkflowPublishedLanguageTranslator.toPublishedPlanId(executionResult.planId());
    var publishedJournal =
        BookWorkflowPublishedLanguageTranslator.toPublished(executionResult.journal());
    return switch (executionResult.status()) {
      case SUCCEEDED -> new LedgerPlanResult.Succeeded(publishedPlanId, publishedJournal);
      case REJECTED -> new LedgerPlanResult.Rejected(publishedPlanId, publishedJournal);
      case ASSERTION_FAILED ->
          new LedgerPlanResult.AssertionFailed(publishedPlanId, publishedJournal);
    };
  }
}
