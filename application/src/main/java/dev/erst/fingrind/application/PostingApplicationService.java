package dev.erst.fingrind.application;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.runtime.PostingCommitResult;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingFactStore postingFactStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the posting application service with its runtime seams. */
  public PostingApplicationService(
      PostingFactStore postingFactStore, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.postingFactStore = Objects.requireNonNull(postingFactStore, "postingFactStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PostEntryResult preflight(PostEntryCommand command) {
    Optional<PostingRejection> rejection = rejectionFor(command);
    if (rejection.isPresent()) {
      return rejected(command, rejection.orElseThrow());
    }
    return new PostEntryResult.PreflightAccepted(
        command.requestProvenance().idempotencyKey(), command.journalEntry().effectiveDate());
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public PostEntryResult commit(PostEntryCommand command) {
    Optional<PostingRejection> rejection = rejectionFor(command);
    if (rejection.isPresent()) {
      return rejected(command, rejection.orElseThrow());
    }

    PostingFact postingFact =
        new PostingFact(
            postingIdGenerator.nextPostingId(),
            command.journalEntry(),
            command.reversalReference(),
            new CommittedProvenance(
                command.requestProvenance(), clock.instant(), command.sourceChannel()));

    return switch (postingFactStore.commit(postingFact)) {
      case PostingCommitResult.Committed committed -> committedResult(committed.postingFact());
      case PostingCommitResult.DuplicateIdempotency _ ->
          rejected(command, new PostingRejection.DuplicateIdempotencyKey());
      case PostingCommitResult.DuplicateReversalTarget duplicateReversalTarget ->
          rejected(
              command,
              new PostingRejection.ReversalAlreadyExists(duplicateReversalTarget.priorPostingId()));
    };
  }

  private Optional<PostingFact> existingPosting(PostEntryCommand command) {
    return postingFactStore.findByIdempotency(command.requestProvenance().idempotencyKey());
  }

  private Optional<PostingRejection> rejectionFor(PostEntryCommand command) {
    if (existingPosting(command).isPresent()) {
      return Optional.of(new PostingRejection.DuplicateIdempotencyKey());
    }
    return ReversalPolicy.rejectionFor(command, postingFactStore);
  }

  private static PostEntryResult.Committed committedResult(PostingFact committedPosting) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt());
  }

  private static PostEntryResult.Rejected rejected(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.Rejected(command.requestProvenance().idempotencyKey(), rejection);
  }
}
