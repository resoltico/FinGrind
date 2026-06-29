package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.time.Clock;
import java.util.Objects;

/**
 * Application service that coordinates one contiguous interim-result sweep into one policy-selected
 * result-holding target.
 */
public final class InterimResultSweepService {
  private final ReportingPeriodCloseStore reportingPeriodCloseStore;
  private final ReportingPeriodCloseExecutionSupport executionSupport;

  /** Creates the interim-result-sweep service with its application-owned seams. */
  public InterimResultSweepService(
      BookLifecycleReader lifecycleReader,
      ReportingPeriodCloseStore reportingPeriodCloseStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.reportingPeriodCloseStore =
        Objects.requireNonNull(reportingPeriodCloseStore, "reportingPeriodCloseStore");
    this.executionSupport =
        new ReportingPeriodCloseExecutionSupport(lifecycleReader, postingIdGenerator, clock);
  }

  /** Sweeps one contiguous reporting period into generated result-holding postings. */
  public InterimResultSweepOutcome interimResultSweep(ReportingPeriod reportingPeriod) {
    return executionSupport.execute(
        reportingPeriod,
        () ->
            new InterimResultSweepOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized()),
        bookIdentity ->
            new InterimResultSweepPlanner(
                KernelAccountingRulesResolver.forBookIdentity(bookIdentity).closePostingPolicy()),
        reportingPeriodCloseStore::interimResultSweep);
  }
}
