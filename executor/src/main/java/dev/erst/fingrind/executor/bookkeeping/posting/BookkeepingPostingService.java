package dev.erst.fingrind.executor.bookkeeping.posting;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping posting service used before any public published-language projection. */
public final class BookkeepingPostingService {
  private final BookStore bookStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the local bookkeeping posting service with its application-owned seams. */
  public BookkeepingPostingService(
      BookStore bookStore, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Validates whether one posting command is admissible for later commit. */
  public PostingPreflightOutcome preflight(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    Optional<BookkeepingPostingRejection> rejection =
        PostingAcceptancePolicy.rejectionFor(command, bookStore);
    if (rejection.isPresent()) {
      return new PostingPreflightOutcome.Rejected(rejection.orElseThrow());
    }
    return new PostingPreflightOutcome.Accepted(
        command.requestProvenance().idempotencyKey(), command.journalEntry().effectiveDate());
  }

  /** Commits one posting command into the selected book using the configured posting id source. */
  public PostingCommitResult commit(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    PostingDraft postingDraft =
        new PostingDraft(
            command.journalEntry(),
            command.postingLineage(),
            PostingKind.STANDARD,
            new CommittedProvenance(
                command.requestProvenance(), clock.instant(), command.sourceChannel()));
    return bookStore.commit(postingDraft, postingIdGenerator);
  }
}
