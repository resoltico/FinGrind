package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Application service that coordinates one contiguous period-result transfer into one
 * policy-selected result-holding target.
 */
public final class PeriodResultTransferService {
  private final BookLifecycleReader lifecycleReader;
  private final PeriodResultTransferStore periodResultTransferStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the transfer-period-result service with its application-owned seams. */
  public PeriodResultTransferService(
      BookLifecycleReader lifecycleReader,
      PeriodResultTransferStore periodResultTransferStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.periodResultTransferStore =
        Objects.requireNonNull(periodResultTransferStore, "periodResultTransferStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Transfers one contiguous reporting period into generated result-holding postings. */
  public PeriodResultTransferOutcome transferPeriodResult(ReportingPeriod reportingPeriod) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new PeriodResultTransferOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    var bookIdentity = lifecycleReader.requireInitializedBookIdentity();
    PeriodResultTransferPlanner planner =
        new PeriodResultTransferPlanner(
            KernelAccountingRulesResolver.forBookIdentity(bookIdentity).resultTransferPolicy());
    return periodResultTransferStore.transferPeriodResult(
        reportingPeriod,
        bookIdentity,
        planner,
        currentUtcDate(),
        clock.instant(),
        postingIdGenerator);
  }

  private java.time.LocalDate currentUtcDate() {
    return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
  }
}
