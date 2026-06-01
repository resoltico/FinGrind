package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import java.time.Instant;
import java.time.LocalDate;

/** Plans and commits one durable period-result transfer under the store-owned mutation boundary. */
@FunctionalInterface
public interface PeriodResultTransferStore {
  /** Attempts one atomic transfer-period-result commit and returns the administration outcome. */
  PeriodResultTransferOutcome transferPeriodResult(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      PeriodResultTransferPlanner planner,
      LocalDate currentUtcDate,
      Instant transferredAt,
      PostingIdGenerator postingIdGenerator);
}
