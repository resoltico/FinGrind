package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service that coordinates one contiguous period-result transfer into one
 * policy-selected result-holding target.
 */
public final class PeriodResultTransferService {
  private final BookLifecycleReader lifecycleReader;
  private final AccountCatalogStore accountCatalogStore;
  private final PostingRangeStore postingRangeStore;
  private final PeriodResultTransferStore periodResultTransferStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the transfer-period-result service with its application-owned seams. */
  public PeriodResultTransferService(
      BookLifecycleReader lifecycleReader,
      AccountCatalogStore accountCatalogStore,
      PostingRangeStore postingRangeStore,
      PeriodResultTransferStore periodResultTransferStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.accountCatalogStore = Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    this.postingRangeStore = Objects.requireNonNull(postingRangeStore, "postingRangeStore");
    this.periodResultTransferStore =
        Objects.requireNonNull(periodResultTransferStore, "periodResultTransferStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Transfers one contiguous reporting period into generated result-holding postings. */
  public PeriodResultTransferOutcome transferPeriodResult(ReportingPeriod reportingPeriod) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    BookLifecycleInspection inspection = lifecycleReader.inspectBook();
    if (!inspection.allowsInitializedWorkflow()) {
      return new PeriodResultTransferOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    BookLifecycleInspection.Initialized initialized =
        (BookLifecycleInspection.Initialized) inspection;
    PeriodResultTransferPlanner planner =
        new PeriodResultTransferPlanner(
            KernelAccountingRulesResolver.forBookIdentity(initialized.bookIdentity())
                .resultTransferPolicy());
    List<RegisteredAccount> accounts = accountCatalogStore.allAccounts();
    PeriodResultTransferPlanner.ResultHoldingSelection resultHoldingSelection =
        planner.resultHoldingAccount(initialized.bookIdentity(), accounts);
    if (resultHoldingSelection
        instanceof PeriodResultTransferPlanner.RejectedResultHoldingSelection rejected) {
      return new PeriodResultTransferOutcome.Rejected(rejected.rejection());
    }

    Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
        planner.closeHorizonRejection(
            reportingPeriod,
            initialized.bookIdentity(),
            currentUtcDate(),
            postingRangeStore.transferredThroughEffectiveDate());
    if (closeHorizonRejection.isPresent()) {
      return new PeriodResultTransferOutcome.Rejected(closeHorizonRejection.orElseThrow());
    }

    Instant transferredAt = clock.instant();
    RegisteredAccount resultHoldingAccount =
        ((PeriodResultTransferPlanner.AcceptedResultHoldingSelection) resultHoldingSelection)
            .account();
    PeriodResultTransferPlanner.PeriodResultTransferPlan closePlan =
        planner.closingPostings(
            reportingPeriod,
            resultHoldingAccount,
            accounts,
            postingRangeStore.postings(reportingPeriod.effectiveDateRange()),
            transferredAt);
    return periodResultTransferStore.transferPeriodResult(
        new PeriodResultTransferDraft(
            reportingPeriod,
            resultHoldingAccount.accountCode(),
            closePlan.transferredTotals(),
            transferredAt,
            closePlan.closingPostings()),
        postingIdGenerator);
  }

  private java.time.LocalDate currentUtcDate() {
    return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
  }
}
