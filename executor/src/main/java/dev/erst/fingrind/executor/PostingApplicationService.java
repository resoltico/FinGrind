package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.core.CommittedProvenance;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingBookSession bookSession;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      PostingBookSession bookSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.bookSession = Objects.requireNonNull(bookSession, "bookSession");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    Optional<PostingRejection> rejection = PostingValidation.rejectionFor(command, bookSession);
    if (rejection.isPresent()) {
      return rejectedPreflight(command, rejection.orElseThrow());
    }
    return new PostEntryResult.PreflightAccepted(
        command.requestProvenance().idempotencyKey(), command.journalEntry().effectiveDate());
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public CommitEntryResult commit(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    PostingDraft postingDraft =
        new PostingDraft(
            command.journalEntry(),
            command.postingLineage(),
            new CommittedProvenance(
                command.requestProvenance(), clock.instant(), command.sourceChannel()));

    return switch (bookSession.commit(postingDraft, postingIdGenerator)) {
      case PostingCommitResult.Committed committed -> committedResult(committed.postingFact());
      case PostingCommitResult.Rejected rejected -> rejectedCommit(command, rejected.rejection());
    };
  }

  private static PostEntryResult.Committed committedResult(PostingFact committedPosting) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt());
  }

  private static PostEntryResult.PreflightRejected rejectedPreflight(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private static PostEntryResult.CommitRejected rejectedCommit(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }
}
