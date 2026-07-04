package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.JournalEntry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Resolved journal facts returned by preflight and commit success surfaces. */
public record ResolvedJournal(
    JournalEntry expandedLines,
    @Nullable AppliedTax appliedTax,
    @Nullable ForeignExchangeDetails foreignExchangeDetails,
    ClassificationResult classification) {
  /** Validates one resolved journal payload. */
  public ResolvedJournal {
    Objects.requireNonNull(expandedLines, "expandedLines");
    Objects.requireNonNull(classification, "classification");
  }
}
