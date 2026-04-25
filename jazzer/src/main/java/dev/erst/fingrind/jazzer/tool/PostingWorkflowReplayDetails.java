package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Stable replay details for committed posting-workflow seeds. */
public record PostingWorkflowReplayDetails(
    ParsedPostingCommandDetails request,
    PostingWorkflowLifecycleDetails lifecycle,
    PostingWorkflowOutcomeDetails outcome)
    implements ReplayDetails {
  public PostingWorkflowReplayDetails {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
  }
}

/** Replay details for posting-workflow inputs that never produced a parsed posting command. */
record UnparsedPostingWorkflowReplayDetails() implements ReplayDetails {}

/**
 * Lifecycle checkpoints that every parsed posting-workflow replay visits before the final outcome.
 */
record PostingWorkflowLifecycleDetails(
    PostingGateDetails uninitialized, PostingGateDetails undeclared, PostingGateDetails inactive) {
  PostingWorkflowLifecycleDetails {
    Objects.requireNonNull(uninitialized, "uninitialized must not be null");
    Objects.requireNonNull(undeclared, "undeclared must not be null");
    Objects.requireNonNull(inactive, "inactive must not be null");
  }
}

/** Deterministic preflight/commit outcome pair recorded for one posting lifecycle gate. */
record PostingGateDetails(
    PostingLifecycleStatus preflightStatus, PostingLifecycleStatus commitStatus) {
  PostingGateDetails {
    Objects.requireNonNull(preflightStatus, "preflightStatus must not be null");
    Objects.requireNonNull(commitStatus, "commitStatus must not be null");
  }
}

/** Final parsed posting-workflow outcome once the lifecycle setup has completed. */
record PostingWorkflowOutcomeDetails(
    PostingLifecycleStatus finalPreflightStatus,
    PostingLifecycleStatus finalCommitStatus,
    PostingLifecycleStatus duplicateStatus,
    boolean storedFactPresent) {
  PostingWorkflowOutcomeDetails {
    Objects.requireNonNull(finalPreflightStatus, "finalPreflightStatus must not be null");
    Objects.requireNonNull(finalCommitStatus, "finalCommitStatus must not be null");
    Objects.requireNonNull(duplicateStatus, "duplicateStatus must not be null");
  }
}
