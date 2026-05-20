package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePolicy;
import dev.erst.fingrind.executor.bookkeeping.policy.CoreBookkeepingPolicyPack;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodCloseStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service that coordinates one contiguous reporting-period close into one
 * policy-selected equity target.
 */
public final class PeriodCloseService {
  private final BookLifecycleReader lifecycleReader;
  private final AccountCatalogStore accountCatalogStore;
  private final PostingRangeStore postingRangeStore;
  private final PeriodCloseStore periodCloseStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;
  private final PeriodClosePlanner planner;

  /** Creates the close-period service with its application-owned seams. */
  public PeriodCloseService(
      BookLifecycleReader lifecycleReader,
      AccountCatalogStore accountCatalogStore,
      PostingRangeStore postingRangeStore,
      PeriodCloseStore periodCloseStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this(
        lifecycleReader,
        accountCatalogStore,
        postingRangeStore,
        periodCloseStore,
        postingIdGenerator,
        clock,
        CoreBookkeepingPolicyPack.current().closePolicy());
  }

  PeriodCloseService(
      BookLifecycleReader lifecycleReader,
      AccountCatalogStore accountCatalogStore,
      PostingRangeStore postingRangeStore,
      PeriodCloseStore periodCloseStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock,
      ClosePolicy closePolicy) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.accountCatalogStore = Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    this.postingRangeStore = Objects.requireNonNull(postingRangeStore, "postingRangeStore");
    this.periodCloseStore = Objects.requireNonNull(periodCloseStore, "periodCloseStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.planner = new PeriodClosePlanner(Objects.requireNonNull(closePolicy, "closePolicy"));
  }

  /** Closes one contiguous reporting period using generated closing-equity postings. */
  public PeriodCloseOutcome closePeriod(ReportingPeriod reportingPeriod) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    BookLifecycleInspection inspection = lifecycleReader.inspectBook();
    if (!inspection.allowsInitializedWorkflow()) {
      return new PeriodCloseOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    BookLifecycleInspection.Initialized initialized =
        (BookLifecycleInspection.Initialized) inspection;
    List<RegisteredAccount> accounts = accountCatalogStore.allAccounts();
    PeriodClosePlanner.ClosingEquitySelection closingEquitySelection =
        planner.closingEquityAccount(initialized.bookIdentity(), accounts);
    if (closingEquitySelection
        instanceof PeriodClosePlanner.RejectedClosingEquitySelection rejected) {
      return new PeriodCloseOutcome.Rejected(rejected.rejection());
    }

    Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
        planner.closeHorizonRejection(
            reportingPeriod,
            initialized.bookIdentity(),
            currentUtcDate(),
            postingRangeStore.closedThroughEffectiveDate());
    if (closeHorizonRejection.isPresent()) {
      return new PeriodCloseOutcome.Rejected(closeHorizonRejection.orElseThrow());
    }

    Instant closedAt = clock.instant();
    RegisteredAccount closingEquityAccount =
        ((PeriodClosePlanner.AcceptedClosingEquitySelection) closingEquitySelection).account();
    PeriodClosePlanner.PeriodClosePlan closePlan =
        planner.closingPostings(
            reportingPeriod,
            closingEquityAccount,
            accounts,
            postingRangeStore.postings(reportingPeriod.effectiveDateRange()),
            closedAt);
    return periodCloseStore.closePeriod(
        new PeriodCloseDraft(
            reportingPeriod,
            closingEquityAccount.accountCode(),
            closePlan.closedTotals(),
            closedAt,
            closePlan.closingPostings()),
        postingIdGenerator);
  }

  private java.time.LocalDate currentUtcDate() {
    return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
  }
}
