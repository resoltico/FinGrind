package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.posting.BookkeepingPostingService;
import dev.erst.fingrind.executor.bookkeeping.posting.PostingPreflightOutcome;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.Objects;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final BookkeepingPostingService bookkeepingPostingService;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      BookStore bookStore, PostingIdGenerator postingIdGenerator, java.time.Clock clock) {
    this.bookkeepingPostingService =
        new BookkeepingPostingService(
            Objects.requireNonNull(bookStore, "bookStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            Objects.requireNonNull(clock, "clock"));
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PreflightEntryResult preflight(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    return switch (bookkeepingPostingService.preflight(command)) {
      case PostingPreflightOutcome.Accepted accepted ->
          new PostEntryResult.PreflightAccepted(
              accepted.idempotencyKey(), accepted.effectiveDate());
      case PostingPreflightOutcome.Rejected rejected ->
          rejectedPreflight(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public CommitEntryResult commit(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    return switch (bookkeepingPostingService.commit(command)) {
      case PostingCommitResult.Committed committed -> committedResult(committed.postingFact());
      case PostingCommitResult.Rejected rejected ->
          rejectedCommit(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  private static PostEntryResult.Committed committedResult(CommittedPosting committedPosting) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt());
  }

  private static PostEntryResult.PreflightRejected rejectedPreflight(
      PostingCommand command, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private static PostEntryResult.CommitRejected rejectedCommit(
      PostingCommand command, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }
}
