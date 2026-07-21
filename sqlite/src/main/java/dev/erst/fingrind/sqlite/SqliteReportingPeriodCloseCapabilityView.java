package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared period-close delegation defaults for SQLite capability wrappers. */
interface SqliteReportingPeriodCloseCapabilityView
    extends SqliteReportingPeriodCloseSession, SqliteLifecycleInspectionCapabilityView {
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
    return storeReadOperations().accountCatalog().allAccounts();
  }

  @Override
  default AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountCatalog().listAccounts(query);
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
  default InterimResultSweepOutcome interimResultSweep(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            sweptAt,
            postingIdGenerator,
            attestationAuthorizer);
  }

  @Override
  default InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(
            throughEffectiveDate,
            bookStartDate,
            bookIdentity,
            planner,
            currentUtcDate,
            sweptAt,
            postingIdGenerator,
            attestationAuthorizer);
  }

  @Override
  default FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .fiscalYearClose(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            closedAt,
            postingIdGenerator,
            attestationAuthorizer);
  }
}
