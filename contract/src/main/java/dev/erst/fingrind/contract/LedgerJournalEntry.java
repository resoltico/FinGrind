package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One per-step journal entry emitted by ledger-plan execution. */
public record LedgerJournalEntry(
    String stepId,
    OperationId operationId,
    LedgerStepStatus status,
    Instant startedAt,
    Instant finishedAt,
    List<LedgerFact> facts,
    Optional<LedgerStepFailure> failure) {
  /** Validates one step journal entry. */
  public LedgerJournalEntry {
    Objects.requireNonNull(stepId, "stepId");
    Objects.requireNonNull(operationId, "operationId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
    failure = failure == null ? Optional.empty() : failure;
    if (stepId.isBlank()) {
      throw new IllegalArgumentException("Ledger journal stepId must not be blank.");
    }
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Ledger journal step finishedAt must not precede startedAt.");
    }
    if (status == LedgerStepStatus.SUCCEEDED && failure.isPresent()) {
      throw new IllegalArgumentException(
          "Successful ledger journal steps must not carry failures.");
    }
    if (status != LedgerStepStatus.SUCCEEDED && failure.isEmpty()) {
      throw new IllegalArgumentException("Failed ledger journal steps must carry a failure.");
    }
  }
}
