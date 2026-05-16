package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;

/** Commits one generated period-close batch into durable storage. */
@FunctionalInterface
public interface PeriodCloseStore {
  /** Attempts one durable close-period commit and returns the administration outcome. */
  PeriodCloseOutcome closePeriod(
      PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator);
}
