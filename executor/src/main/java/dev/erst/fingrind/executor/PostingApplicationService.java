package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.posting.BookkeepingPostingService;
import dev.erst.fingrind.executor.bookkeeping.posting.PostingPreflightOutcome;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.Objects;

/** Application service that owns preflight and commit behavior for posting entries. */
public final class PostingApplicationService {
  private final PostingValidationStore validationStore;
  private final BookkeepingPostingService bookkeepingPostingService;
  private final PostingCommandAdmission commandAdmission;

  /** Creates the posting application service with its application-owned seams. */
  public PostingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      java.time.Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    java.time.Clock checkedClock = Objects.requireNonNull(clock, "clock");
    this.commandAdmission = new PostingCommandAdmission(this.validationStore, checkedClock);
    this.bookkeepingPostingService =
        new BookkeepingPostingService(
            this.validationStore,
            Objects.requireNonNull(commitStore, "commitStore"),
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            checkedClock);
  }

  /** Validates a request and reports whether a later commit attempt is admissible. */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    switch (commandAdmission.idempotencyOutcomeFor(command)) {
      case PostingCommandAdmission.IdempotencyOutcome.Replay replay -> {
        return new PostEntryResult.PreflightAccepted(
            command.requestProvenance().idempotencyKey(),
            replay.posting().journalEntry().effectiveDate(),
            commandAdmission.resolvedJournal(replay.posting()));
      }
      case PostingCommandAdmission.IdempotencyOutcome.Conflict conflict -> {
        return rejectedPreflight(command, conflict.rejection());
      }
      case PostingCommandAdmission.IdempotencyOutcome.Fresh _ -> {
        // Only new keys enter state-dependent command admission.
      }
    }
    java.util.Optional<PostingRejection> rejection = commandAdmission.rejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedPreflight(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = commandAdmission.localPostingCommand(command);
    return switch (bookkeepingPostingService.preflight(postingCommand)) {
      case PostingPreflightOutcome.Accepted accepted ->
          new PostEntryResult.PreflightAccepted(
              accepted.idempotencyKey(),
              accepted.effectiveDate(),
              commandAdmission.resolvedJournal(command, postingCommand));
      case PostingPreflightOutcome.Rejected rejected ->
          rejectedPreflight(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Commits a request as one durable posting fact or returns a deterministic rejection. */
  public CommitEntryResult commit(
      PostEntryCommand command, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    switch (commandAdmission.idempotencyOutcomeFor(command)) {
      case PostingCommandAdmission.IdempotencyOutcome.Replay replay -> {
        return replayedResult(replay.posting(), commandAdmission.resolvedJournal(replay.posting()));
      }
      case PostingCommandAdmission.IdempotencyOutcome.Conflict conflict -> {
        return rejectedCommit(command, conflict.rejection());
      }
      case PostingCommandAdmission.IdempotencyOutcome.Fresh _ -> {
        // The durable commit store repeats idempotency admission inside its transaction.
      }
    }
    java.util.Optional<PostingRejection> rejection = commandAdmission.rejectionFor(command);
    if (rejection.isPresent()) {
      return rejectedCommit(command, rejection.orElseThrow());
    }
    PostingCommand postingCommand = commandAdmission.localPostingCommand(command);
    return switch (bookkeepingPostingService.commit(postingCommand, attestationAuthorizer)) {
      case PostingCommitResult.Appended appended ->
          appendedResult(
              appended.postingFact(),
              appended.attestationAppend(),
              commandAdmission.resolvedJournal(command, postingCommand));
      case PostingCommitResult.Replayed replayed ->
          replayedResult(
              replayed.postingFact(), commandAdmission.resolvedJournal(command, postingCommand));
      case PostingCommitResult.Rejected rejected ->
          rejectedCommit(
              command, BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  private static PostEntryResult.Committed appendedResult(
      CommittedPosting committedPosting,
      AttestationAppendOutcome.Appended attestationAppend,
      ResolvedJournal resolvedJournal) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt(),
        false,
        resolvedJournal,
        AttestationCommitProjection.fromVerifiedAppend(attestationAppend));
  }

  private static PostEntryResult.Committed replayedResult(
      CommittedPosting committedPosting, ResolvedJournal resolvedJournal) {
    return new PostEntryResult.Committed(
        committedPosting.postingId(),
        committedPosting.provenance().requestProvenance().idempotencyKey(),
        committedPosting.journalEntry().effectiveDate(),
        committedPosting.provenance().recordedAt(),
        true,
        resolvedJournal,
        null);
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
