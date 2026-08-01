package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local workflow execution result before published-language projection. */
public record BookWorkflowExecutionResult(
    BookWorkflowPlanId planId,
    BookWorkflowExecutionStatus status,
    BookWorkflowExecutionJournal journal,
    @Nullable AttestationCommit attestationCommit) {
  public BookWorkflowExecutionResult {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(journal, "journal");
    if (status != BookWorkflowExecutionStatus.SUCCEEDED && attestationCommit != null) {
      throw new IllegalArgumentException(
          "Only a successfully committed plan may report an attestation commitment.");
    }
  }
}
