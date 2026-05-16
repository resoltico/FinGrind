package dev.erst.fingrind.executor.bookkeeping.posting;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping posting service used before any public published-language projection. */
public final class BookkeepingPostingService {
  private final PostingValidationStore validationStore;
  private final PostingCommitStore commitStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the local bookkeeping posting service with its application-owned seams. */
  public BookkeepingPostingService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.commitStore = Objects.requireNonNull(commitStore, "commitStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Validates whether one posting command is admissible for later commit. */
  public PostingPreflightOutcome preflight(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    Optional<BookkeepingPostingRejection> rejection =
        PostingAcceptancePolicy.rejectionFor(command, validationStore);
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
            command.postingKind(),
            new CommittedProvenance(
                command.requestProvenance(), clock.instant(), command.sourceChannel()));
    return commitStore.commit(postingDraft, postingIdGenerator);
  }
}
