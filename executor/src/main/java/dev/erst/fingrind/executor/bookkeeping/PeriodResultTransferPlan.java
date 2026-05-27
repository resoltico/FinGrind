package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.List;
import java.util.Objects;

/**
 * One complete result-transfer plan containing durable drafts and the close totals they produce.
 */
public record PeriodResultTransferPlan(
    List<PostingDraft> closingPostings, List<CurrencyBalance> transferredTotals) {
  public PeriodResultTransferPlan {
    Objects.requireNonNull(closingPostings, "closingPostings");
    Objects.requireNonNull(transferredTotals, "transferredTotals");
    closingPostings = List.copyOf(closingPostings);
    transferredTotals = List.copyOf(transferredTotals);
  }
}
