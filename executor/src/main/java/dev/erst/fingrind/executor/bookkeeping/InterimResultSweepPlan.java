package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.List;
import java.util.Objects;

/**
 * One complete interim-result-sweep plan containing durable drafts and the close totals they
 * produce.
 */
public record InterimResultSweepPlan(
    List<PostingDraft> closingPostings, List<CurrencyBalance> sweptTotals) {
  public InterimResultSweepPlan {
    Objects.requireNonNull(closingPostings, "closingPostings");
    Objects.requireNonNull(sweptTotals, "sweptTotals");
    closingPostings = List.copyOf(closingPostings);
    sweptTotals = List.copyOf(sweptTotals);
  }
}
