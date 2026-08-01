package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;

/** Coordinates universal and context-owned reversal admission checks. */
final class ReversalAcceptancePolicy {
  private ReversalAcceptancePolicy() {}

  /** Returns the first deterministic reversal rejection for the supplied attempt, if any. */
  static Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    return switch (postingRequest.postingLineage()) {
      case PostingLineageModel.Direct _ -> Optional.empty();
      case PostingLineageModel.Reversal reversal -> {
        PostingId priorPostingId = reversal.reference().priorPostingId();
        Optional<CommittedPosting> priorPosting = book.findPosting(priorPostingId);
        if (priorPosting.isEmpty()) {
          yield Optional.of(new BookkeepingPostingRejection.ReversalTargetNotFound(priorPostingId));
        }
        yield rejectionForReversal(postingRequest.journalEntry(), priorPosting.orElseThrow(), book);
      }
    };
  }

  private static Optional<BookkeepingPostingRejection> rejectionForReversal(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    PostingId priorPostingId = priorPosting.postingId();
    if (priorPosting.postingLineage().isReversal()) {
      return Optional.of(new ReversalTargetIsReversal(priorPostingId));
    }
    if (book.findReversalFor(priorPostingId).isPresent()) {
      return Optional.of(new BookkeepingPostingRejection.ReversalAlreadyExists(priorPostingId));
    }
    Optional<BookkeepingPostingRejection> contextRejection =
        ReversalAccrualCutoffAcceptancePolicy.rejectionFor(candidateReversal, priorPosting, book);
    if (contextRejection.isPresent()) {
      return contextRejection;
    }
    contextRejection =
        ReversalLatvianPayrollAcceptancePolicy.rejectionFor(candidateReversal, priorPosting, book);
    if (contextRejection.isPresent()) {
      return contextRejection;
    }
    contextRejection = ReversalLifecycleAcceptancePolicy.rejectionFor(priorPosting, book);
    if (contextRejection.isPresent()) {
      return contextRejection;
    }
    if (!JournalReversalEquivalence.negates(candidateReversal, priorPosting.journalEntry())) {
      return Optional.of(
          new BookkeepingPostingRejection.ReversalDoesNotNegateTarget(priorPostingId));
    }
    return Optional.empty();
  }
}
