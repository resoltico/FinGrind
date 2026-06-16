package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared period-close delegation defaults for SQLite capability wrappers. */
interface SqlitePeriodResultTransferCapabilityView
    extends SqlitePeriodResultTransferSession, SqliteLifecycleInspectionCapabilityView {
  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  @Override
  default dev.erst.fingrind.executor.spi.BookLifecycleInspection inspectBook() {
    return SqliteLifecycleInspectionCapabilityView.super.inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    return SqliteLifecycleInspectionCapabilityView.super.allowsInitializedWorkflow();
  }

  @Override
  default BookIdentity requireInitializedBookIdentity() {
    return SqliteLifecycleInspectionCapabilityView.super.requireInitializedBookIdentity();
  }

  @Override
  default List<RegisteredAccount> allAccounts() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().allAccounts();
  }

  @Override
  default AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().listAccounts(query);
  }

  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }

  @Override
  default PeriodResultTransferOutcome transferPeriodResult(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      PeriodResultTransferPlanner planner,
      LocalDate currentUtcDate,
      Instant transferredAt,
      PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .transferPeriodResult(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            transferredAt,
            postingIdGenerator);
  }
}
