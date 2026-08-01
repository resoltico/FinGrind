package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Validates one authored posting request without needing a posting-mutation capability. */
public final class PostingPreflightService {
  private final PostingValidationStore validationStore;
  private final PostingAcceptancePolicy acceptancePolicy;
  private final PostingCommandAdmission commandAdmission;

  /** Creates one read-safe posting preflight service. */
  public PostingPreflightService(PostingValidationStore validationStore, Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    acceptancePolicy = PostingAcceptancePolicy.currentKernel();
    commandAdmission =
        new PostingCommandAdmission(this.validationStore, Objects.requireNonNull(clock, "clock"));
  }

  /**
   * Validates one posting command without reserving an identifier or changing protected-book state.
   */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    PostEntryCommand checkedCommand = Objects.requireNonNull(command, "command");
    Optional<PostingRejection> rejection = commandAdmission.rejectionFor(checkedCommand);
    if (rejection.isPresent()) {
      return rejectedPreflight(checkedCommand, rejection.orElseThrow());
    }
    PostingCommand postingCommand = commandAdmission.localPostingCommand(checkedCommand);
    return switch (acceptancePolicy.decisionFor(postingCommand, validationStore)) {
      case PostingAcceptancePolicy.Decision.Replay replay ->
          new PostEntryResult.PreflightAccepted(
              checkedCommand.requestProvenance().idempotencyKey(),
              replay.postingFact().journalEntry().effectiveDate(),
              commandAdmission.resolvedJournal(checkedCommand, postingCommand));
      case PostingAcceptancePolicy.Decision.Accepted accepted ->
          new PostEntryResult.PreflightAccepted(
              checkedCommand.requestProvenance().idempotencyKey(),
              accepted.acceptedPosting().journalEntry().effectiveDate(),
              commandAdmission.resolvedJournal(checkedCommand, postingCommand));
      case PostingAcceptancePolicy.Decision.Rejected rejected ->
          rejectedPreflight(
              checkedCommand,
              BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  private static PostEntryResult.PreflightRejected rejectedPreflight(
      PostEntryCommand command, PostingRejection rejection) {
    return new PostEntryResult.PreflightRejected(
        command.requestProvenance().idempotencyKey(), rejection);
  }
}
