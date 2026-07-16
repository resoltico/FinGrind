package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mutation surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreMutationView {
  /** Returns the thread-ownership guard for this store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the mutation operations owner for this store. */
  SqliteStoreMutationOperations storeMutationOperations();

  /** Initializes a previously unopened protected book. */
  default BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().openBook(initializedAt, bookIdentity, seededAccounts);
  }

  /** Declares a new account in the protected book. */
  default AccountDeclarationOutcome declareAccount(
      AccountDeclaration declaration, Instant declaredAt) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().declareAccount(declaration, declaredAt);
  }

  /** Commits one posting draft into the protected book. */
  default PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations().commit(postingDraft, postingIdGenerator);
  }

  /** Commits a generated interim-result sweep into the protected book. */
  default InterimResultSweepOutcome interimResultSweep(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(
            reportingPeriod, bookIdentity, planner, currentUtcDate, sweptAt, postingIdGenerator);
  }

  /** Commits a preplanned interim-result sweep into the protected book. */
  default InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft, PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(interimResultSweepDraft, postingIdGenerator);
  }

  /** Commits one fiscal-year close into the protected book. */
  default FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .fiscalYearClose(
            reportingPeriod, bookIdentity, planner, currentUtcDate, closedAt, postingIdGenerator);
  }
}
