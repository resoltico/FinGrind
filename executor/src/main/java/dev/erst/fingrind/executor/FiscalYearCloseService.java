package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.time.Clock;
import java.util.Objects;

/** Application service that coordinates one fiscal-year close. */
public final class FiscalYearCloseService {
  private final ReportingPeriodCloseStore closeStore;
  private final ReportingPeriodCloseExecutionSupport executionSupport;

  /** Creates the fiscal-year-close service with its application-owned seams. */
  public FiscalYearCloseService(
      BookLifecycleReader lifecycleReader,
      ReportingPeriodCloseStore closeStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.closeStore = Objects.requireNonNull(closeStore, "closeStore");
    this.executionSupport =
        new ReportingPeriodCloseExecutionSupport(lifecycleReader, postingIdGenerator, clock);
  }

  /** Closes one fiscal year into capital and retained accumulated equity. */
  public FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod, AttestationOperationAuthorizer attestationAuthorizer) {
    return executionSupport.execute(
        reportingPeriod,
        () ->
            new FiscalYearCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized()),
        FiscalYearClosePlanner::forBookIdentity,
        (period, bookIdentity, planner, currentUtcDate, closedAt, postingIdGenerator) ->
            closeStore.fiscalYearClose(
                period,
                bookIdentity,
                planner,
                currentUtcDate,
                closedAt,
                postingIdGenerator,
                attestationAuthorizer));
  }

  /** Closes the fiscal year identified by the selected label. */
  public FiscalYearCloseOutcome fiscalYearClose(
      int fiscalYearLabel, AttestationOperationAuthorizer attestationAuthorizer) {
    return executionSupport.execute(
        () ->
            new FiscalYearCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized()),
        FiscalYearClosePlanner::forBookIdentity,
        (bookIdentity, planner, currentUtcDate, closedAt, postingIdGenerator) ->
            closeStore.fiscalYearClose(
                planner.reportingPeriod(bookIdentity, fiscalYearLabel),
                bookIdentity,
                planner,
                currentUtcDate,
                closedAt,
                postingIdGenerator,
                attestationAuthorizer));
  }
}
