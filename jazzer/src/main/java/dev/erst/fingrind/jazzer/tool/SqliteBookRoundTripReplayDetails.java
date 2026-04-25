package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Stable replay details for committed SQLite round-trip seeds. */
public record SqliteBookRoundTripReplayDetails(
    ParsedPostingCommandDetails request,
    SqliteBookRoundTripLifecycleDetails lifecycle,
    SqliteBookRoundTripOutcomeDetails outcome)
    implements ReplayDetails {
  public SqliteBookRoundTripReplayDetails {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
  }
}

/** Replay details for SQLite round-trip inputs that never produced a parsed posting command. */
record UnparsedSqliteBookRoundTripReplayDetails() implements ReplayDetails {}

/** Lifecycle checkpoints recorded before the final SQLite round-trip outcome. */
record SqliteBookRoundTripLifecycleDetails(
    PostingLifecycleStatus uninitializedCommitStatus,
    PostingLifecycleStatus undeclaredCommitStatus,
    PostingLifecycleStatus inactiveCommitStatus) {
  SqliteBookRoundTripLifecycleDetails {
    Objects.requireNonNull(uninitializedCommitStatus, "uninitializedCommitStatus must not be null");
    Objects.requireNonNull(undeclaredCommitStatus, "undeclaredCommitStatus must not be null");
    Objects.requireNonNull(inactiveCommitStatus, "inactiveCommitStatus must not be null");
  }
}

/** Final parsed SQLite round-trip outcome after lifecycle setup completes. */
record SqliteBookRoundTripOutcomeDetails(
    PostingLifecycleStatus finalCommitStatus,
    PostingLifecycleStatus reloadStatus,
    PostingLifecycleStatus duplicateStatus,
    boolean storedFactPresent) {
  SqliteBookRoundTripOutcomeDetails {
    Objects.requireNonNull(finalCommitStatus, "finalCommitStatus must not be null");
    Objects.requireNonNull(reloadStatus, "reloadStatus must not be null");
    Objects.requireNonNull(duplicateStatus, "duplicateStatus must not be null");
  }
}
