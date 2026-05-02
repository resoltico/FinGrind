package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import java.util.Objects;

/** Typed concurrent-commit outcome for SQLite round-trip coverage. */
sealed interface ConcurrentCommitOutcome
    permits ConcurrentCommitDecision, ConcurrentCommitRuntimeFailure {}

record ConcurrentCommitDecision(ContractDecision<CommitEntryResult> decision)
    implements ConcurrentCommitOutcome {
  ConcurrentCommitDecision {
    Objects.requireNonNull(decision, "decision");
  }
}

record ConcurrentCommitRuntimeFailure(RuntimeException runtimeFailure)
    implements ConcurrentCommitOutcome {
  ConcurrentCommitRuntimeFailure {
    Objects.requireNonNull(runtimeFailure, "runtimeFailure");
  }
}
