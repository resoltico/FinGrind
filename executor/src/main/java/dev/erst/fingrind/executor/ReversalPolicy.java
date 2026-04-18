package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PostingRequest;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Validates reversal lineage rules before a posting reaches the durable store. */
final class ReversalPolicy {
  private ReversalPolicy() {}

  /** Returns the first deterministic reversal rejection for the supplied command, if any. */
  static Optional<PostingRejection> rejectionFor(
      PostingRequest postingRequest, PostingValidationBook book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    return switch (postingRequest.postingLineage()) {
      case PostingLineage.Direct _ -> Optional.empty();
      case PostingLineage.Reversal reversal -> {
        PostingId priorPostingId = reversal.reference().priorPostingId();
        Optional<PostingFact> priorPosting = book.findPosting(priorPostingId);
        if (priorPosting.isEmpty()) {
          yield Optional.of(new PostingRejection.ReversalTargetNotFound(priorPostingId));
        }
        yield reversalRejection(postingRequest.journalEntry(), priorPosting.orElseThrow(), book);
      }
    };
  }

  private static Optional<PostingRejection> reversalRejection(
      JournalEntry candidateReversal, PostingFact priorPosting, PostingValidationBook book) {
    PostingId priorPostingId = priorPosting.postingId();
    if (book.findReversalFor(priorPostingId).isPresent()) {
      return Optional.of(new PostingRejection.ReversalAlreadyExists(priorPostingId));
    }
    if (!negates(candidateReversal, priorPosting.journalEntry())) {
      return Optional.of(new PostingRejection.ReversalDoesNotNegateTarget(priorPostingId));
    }
    return Optional.empty();
  }

  private static boolean negates(JournalEntry candidateReversal, JournalEntry original) {
    return normalizedLines(candidateReversal).equals(negatedLines(original));
  }

  private static Map<LineFingerprint, Long> normalizedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .collect(Collectors.groupingBy(LineFingerprint::from, Collectors.counting()));
  }

  private static Map<LineFingerprint, Long> negatedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .map(LineFingerprint::negatedFrom)
        .collect(Collectors.groupingBy(fingerprint -> fingerprint, Collectors.counting()));
  }

  /** Canonical fingerprint for one journal line when comparing reversal equivalence. */
  private record LineFingerprint(
      dev.erst.fingrind.core.AccountCode accountCode,
      JournalLine.EntrySide side,
      dev.erst.fingrind.core.PositiveMoney amount) {
    /** Builds a fingerprint that preserves one concrete journal line verbatim. */
    static LineFingerprint from(JournalLine line) {
      return new LineFingerprint(line.accountCode(), line.side(), line.amount());
    }

    /** Builds the fingerprint expected in a full reversal of one journal line. */
    static LineFingerprint negatedFrom(JournalLine line) {
      return new LineFingerprint(line.accountCode(), opposite(line.side()), line.amount());
    }

    private static JournalLine.EntrySide opposite(JournalLine.EntrySide side) {
      return switch (side) {
        case DEBIT -> JournalLine.EntrySide.CREDIT;
        case CREDIT -> JournalLine.EntrySide.DEBIT;
      };
    }
  }
}
