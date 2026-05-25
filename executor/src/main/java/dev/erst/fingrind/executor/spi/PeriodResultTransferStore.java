package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;

/** Commits one generated period-result-transfer batch into durable storage. */
@FunctionalInterface
public interface PeriodResultTransferStore {
  /** Attempts one durable transfer-period-result commit and returns the administration outcome. */
  PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator);
}
