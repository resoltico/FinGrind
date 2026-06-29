package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import java.time.Instant;
import java.time.LocalDate;

/** Plans and commits durable close operations under the store-owned mutation boundary. */
public interface ReportingPeriodCloseStore {
  /** Attempts one atomic interim-result-sweep commit and returns the administration outcome. */
  InterimResultSweepOutcome interimResultSweep(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator);

  /** Attempts one atomic fiscal-year-close commit and returns the administration outcome. */
  FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator);
}
