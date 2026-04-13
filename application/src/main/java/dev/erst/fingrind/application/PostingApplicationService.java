package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalLine;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final BookSession bookSession;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      BookSession bookSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.bookSession = Objects.requireNonNull(bookSession, "bookSession");
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

    return switch (bookSession.commit(postingFact)) {
      case PostingCommitResult.Committed committed -> committedResult(committed.postingFact());
      case PostingCommitResult.BookNotInitialized _ ->
          rejected(command, new PostingRejection.BookNotInitialized());
      case PostingCommitResult.UnknownAccount unknownAccount ->
          rejected(command, new PostingRejection.UnknownAccount(unknownAccount.accountCode()));
      case PostingCommitResult.InactiveAccount inactiveAccount ->
          rejected(command, new PostingRejection.InactiveAccount(inactiveAccount.accountCode()));
      case PostingCommitResult.DuplicateIdempotency _ ->
          rejected(command, new PostingRejection.DuplicateIdempotencyKey());
      case PostingCommitResult.DuplicateReversalTarget duplicateReversalTarget ->
          rejected(
              command,
              new PostingRejection.ReversalAlreadyExists(duplicateReversalTarget.priorPostingId()));
    };
  }

  private Optional<PostingFact> existingPosting(PostEntryCommand command) {
    return bookSession.findByIdempotency(command.requestProvenance().idempotencyKey());
  }

  private Optional<PostingRejection> rejectionFor(PostEntryCommand command) {
    if (!bookSession.isInitialized()) {
      return Optional.of(new PostingRejection.BookNotInitialized());
    }
    Optional<PostingRejection> accountRejection = accountRejection(command);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    if (existingPosting(command).isPresent()) {
      return Optional.of(new PostingRejection.DuplicateIdempotencyKey());
    }
    return ReversalPolicy.rejectionFor(command, bookSession);
  }

  private Optional<PostingRejection> accountRejection(PostEntryCommand command) {
    for (JournalLine line : command.journalEntry().lines()) {
      Optional<DeclaredAccount> account = bookSession.findAccount(line.accountCode());
      if (account.isEmpty()) {
        return unknownAccount(line.accountCode());
      }
      if (!account.orElseThrow().active()) {
        return inactiveAccount(line.accountCode());
      }
    }
    return Optional.empty();
  }

  private static Optional<PostingRejection> unknownAccount(AccountCode accountCode) {
    return Optional.of(new PostingRejection.UnknownAccount(accountCode));
  }

  private static Optional<PostingRejection> inactiveAccount(AccountCode accountCode) {
    return Optional.of(new PostingRejection.InactiveAccount(accountCode));
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
