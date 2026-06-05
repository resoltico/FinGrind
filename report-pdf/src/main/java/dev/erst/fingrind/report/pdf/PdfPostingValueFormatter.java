package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import java.util.Objects;

/** Shared posting-specific formatting helpers for FinGrind PDF reports. */
final class PdfPostingValueFormatter {
  private PdfPostingValueFormatter() {}

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-transfer postings";
    };
  }

  static String reversalTarget(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(not a reversal)");
  }

  static String postingRole(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String displayPostingKind(PostingKind postingKind) {
    Objects.requireNonNull(postingKind, "postingKind");
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_RESULT_TRANSFER -> "Period result transfer";
      case OPENING_BALANCE -> "Opening balance";
    };
  }
}
