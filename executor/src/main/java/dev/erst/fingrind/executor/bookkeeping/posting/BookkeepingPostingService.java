package dev.erst.fingrind.executor.bookkeeping.posting;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.util.Objects;

/** Local bookkeeping posting service used before any public published-language projection. */
public final class BookkeepingPostingService {
  private final PostingValidationStore validationStore;
  private final PostingCommitStore commitStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;
  private final PostingAcceptancePolicy acceptancePolicy;

  /** Creates the local bookkeeping posting service with one explicit bookkeeping policy pack. */
  public BookkeepingPostingService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.commitStore = Objects.requireNonNull(commitStore, "commitStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.acceptancePolicy = PostingAcceptancePolicy.currentKernel();
  }

  /** Validates whether one posting command is admissible for later commit. */
  public PostingPreflightOutcome preflight(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    return switch (acceptancePolicy.decisionFor(command, validationStore)) {
      case PostingAcceptancePolicy.Decision.Replay replay ->
          new PostingPreflightOutcome.Accepted(
              command.requestProvenance().idempotencyKey(),
              replay.postingFact().journalEntry().effectiveDate());
      case PostingAcceptancePolicy.Decision.Rejected rejected ->
          new PostingPreflightOutcome.Rejected(rejected.rejection());
      case PostingAcceptancePolicy.Decision.Accepted accepted ->
          new PostingPreflightOutcome.Accepted(
              command.requestProvenance().idempotencyKey(),
              accepted.acceptedPosting().journalEntry().effectiveDate());
    };
  }

  /** Commits one posting command into the selected book using the configured posting id source. */
  public PostingCommitResult commit(PostingCommand command) {
    Objects.requireNonNull(command, "command");
    return switch (acceptancePolicy.decisionFor(command, validationStore)) {
      case PostingAcceptancePolicy.Decision.Replay replay ->
          new PostingCommitResult.Committed(replay.postingFact(), true);
      case PostingAcceptancePolicy.Decision.Rejected rejected ->
          new PostingCommitResult.Rejected(rejected.rejection());
      case PostingAcceptancePolicy.Decision.Accepted accepted ->
          commitStore.commit(
              new PostingDraft(
                  accepted.acceptedPosting().journalEntry(),
                  accepted.acceptedPosting().postingLineage(),
                  accepted.acceptedPosting().postingKind(),
                  accepted.acceptedPosting().postingOriginKind(),
                  accepted.acceptedPosting().evidence(),
                  accepted.requestFingerprint(),
                  new CommittedProvenance(
                      accepted.acceptedPosting().requestProvenance(),
                      clock.instant(),
                      accepted.acceptedPosting().sourceChannel()),
                  accepted.acceptedPosting().callerAuthoredEntry().orElse(null),
                  accepted.acceptedPosting().resolvedOriginatingEntry().orElse(null)),
              postingIdGenerator);
    };
  }
}
