package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.posting.BookkeepingPostingService;
import dev.erst.fingrind.executor.bookkeeping.posting.PostingPreflightOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.Objects;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingValidationStore validationStore;
  private final BookkeepingPostingService bookkeepingPostingService;
  private final PostEntrySemanticsPolicy entryAcceptancePolicy;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      java.time.Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.entryAcceptancePolicy = PostEntrySemanticsPolicy.currentKernel();
    this.bookkeepingPostingService =
        new BookkeepingPostingService(
            this.validationStore,
            Objects.requireNonNull(commitStore, "commitStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            Objects.requireNonNull(clock, "clock"));
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    java.util.Optional<PostingRejection> rejection = applicationRejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedPreflight(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = localPostingCommand(command);
    return switch (bookkeepingPostingService.preflight(postingCommand)) {
      case PostingPreflightOutcome.Accepted accepted ->
          new PostEntryResult.PreflightAccepted(
              accepted.idempotencyKey(), accepted.effectiveDate());
      case PostingPreflightOutcome.Rejected rejected ->
          rejectedPreflight(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public CommitEntryResult commit(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    java.util.Optional<PostingRejection> rejection = applicationRejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedCommit(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = localPostingCommand(command);
    return switch (bookkeepingPostingService.commit(postingCommand)) {
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
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private static PostEntryResult.CommitRejected rejectedCommit(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.CommitRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }

  private boolean bookNotInitialized() {
    return !(validationStore.inspectBook() instanceof BookLifecycleInspection.Initialized);
  }

  private java.util.Optional<PostingRejection> applicationRejectionFor(PostEntryCommand command) {
    if (bookNotInitialized()) {
      return java.util.Optional.of(
          BookkeepingPublishedLanguageTranslator.toPublished(
              new BookkeepingPostingRejection.BookNotInitialized()));
    }
    return entryAcceptancePolicy
        .rejectionFor(command, validationStore)
        .map(BookkeepingPublishedLanguageTranslator::toPublished);
  }

  private PostingCommand localPostingCommand(PostEntryCommand command) {
    return PostEntryCommandTranslator.toPostingCommand(command);
  }
}
