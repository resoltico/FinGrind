package dev.erst.fingrind.application;

import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Validates correction lineage rules before a posting reaches the durable store. */
final class CorrectionPolicy {
  private CorrectionPolicy() {}

  /** Returns the first deterministic correction rejection for the supplied command, if any. */
  static Optional<PostingRejection> rejectionFor(
      PostEntryCommand command, PostingFactStore postingFactStore) {
    Optional<CorrectionReference> correctionReference = command.correctionReference();
    boolean reasonPresent = command.requestProvenance().reason().isPresent();
    if (correctionReference.isEmpty()) {
      return reasonPresent
          ? Optional.of(new PostingRejection.CorrectionReasonForbidden())
          : Optional.empty();
    }
    if (!reasonPresent) {
      return Optional.of(new PostingRejection.CorrectionReasonRequired());
    }

    PostingId priorPostingId = correctionReference.orElseThrow().priorPostingId();
    Optional<PostingFact> priorPosting = postingFactStore.findByPostingId(priorPostingId);
    if (priorPosting.isEmpty()) {
      return Optional.of(new PostingRejection.CorrectionTargetNotFound(priorPostingId));
    }

    return switch (correctionReference.orElseThrow().kind()) {
      case AMENDMENT -> Optional.empty();
      case REVERSAL ->
          reversalRejection(command.journalEntry(), priorPosting.orElseThrow(), postingFactStore);
    };
  }

  private static Optional<PostingRejection> reversalRejection(
      JournalEntry candidateReversal, PostingFact priorPosting, PostingFactStore postingFactStore) {
    PostingId priorPostingId = priorPosting.postingId();
    if (postingFactStore.findReversalFor(priorPostingId).isPresent()) {
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
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    /** Builds a fingerprint that preserves one concrete journal line verbatim. */
    static LineFingerprint from(JournalLine line) {
      return new LineFingerprint(
          line.accountCode().value(),
          line.side(),
          line.amount().currencyCode().value(),
          line.amount().amount().toPlainString());
    }

    /** Builds the fingerprint expected in a full reversal of one journal line. */
    static LineFingerprint negatedFrom(JournalLine line) {
      return new LineFingerprint(
          line.accountCode().value(),
          opposite(line.side()),
          line.amount().currencyCode().value(),
          line.amount().amount().toPlainString());
    }

    private static JournalLine.EntrySide opposite(JournalLine.EntrySide side) {
      return switch (side) {
        case DEBIT -> JournalLine.EntrySide.CREDIT;
        case CREDIT -> JournalLine.EntrySide.DEBIT;
      };
    }
  }
}
