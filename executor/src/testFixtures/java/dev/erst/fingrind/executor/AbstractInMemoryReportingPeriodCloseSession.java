package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlan;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared in-memory reporting-period close fixture state for executor tests. */
abstract class AbstractInMemoryReportingPeriodCloseSession extends AbstractInMemoryPostingSession
    implements ReportingPeriodCloseStore {
  protected final List<SweptInterimResult> transferredPeriodResults = new ArrayList<>();
  protected final List<ClosedFiscalYearRecord> closedFiscalYears = new ArrayList<>();

  @Override
  public Optional<LocalDate> transferredThroughEffectiveDate() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            transferredPeriodResults.stream()
                .map(sweptInterimResult -> sweptInterimResult.reportingPeriod().effectiveDateTo())
                .max(Comparator.naturalOrder()));
  }

  @Override
  public InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new InterimResultSweepOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          List<RegisteredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(java.util.Comparator.comparing(account -> account.accountCode().value()))
                  .toList();
          var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
          if (resultHoldingSelection
              instanceof
              dev.erst.fingrind.executor.bookkeeping.RejectedInterimResultTargetSelection
                  rejected) {
            return new InterimResultSweepOutcome.Rejected(rejected.rejection());
          }
          var closeHorizonRejection =
              planner.closeHorizonRejection(
                  reportingPeriod, bookIdentity, currentUtcDate, transferredThroughEffectiveDate());
          if (closeHorizonRejection.isPresent()) {
            return new InterimResultSweepOutcome.Rejected(closeHorizonRejection.orElseThrow());
          }
          RegisteredAccount resultHoldingAccount =
              ((dev.erst.fingrind.executor.bookkeeping.AcceptedInterimResultTargetSelection)
                      resultHoldingSelection)
                  .account();
          InterimResultSweepPlan closePlan =
              planner.closingPostings(
                  reportingPeriod,
                  resultHoldingAccount,
                  accounts,
                  postings(reportingPeriod.effectiveDateRange()),
                  sweptAt);
          return interimResultSweep(
              new InterimResultSweepDraft(
                  reportingPeriod,
                  resultHoldingAccount.accountCode(),
                  closePlan.sweptTotals(),
                  sweptAt,
                  closePlan.closingPostings()),
              postingIdGenerator);
        });
  }

  @Override
  public InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(bookStartDate, "bookStartDate");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return interimResultSweep(
        planner.reportingPeriod(
            throughEffectiveDate, bookStartDate, bookIdentity, transferredThroughEffectiveDate()),
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator);
  }

  InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft, PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(interimResultSweepDraft);
    Objects.requireNonNull(postingIdGenerator);
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new InterimResultSweepOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }

          InMemoryBookSessionSnapshot rollbackSnapshot = snapshotState();
          List<PostingId> sweepPostingIds = new ArrayList<>();
          boolean committed = false;
          try {
            for (dev.erst.fingrind.executor.spi.PostingDraft closingPostingDraft :
                interimResultSweepDraft.closingPostings()) {
              PostingCommitResult commitResult = commit(closingPostingDraft, postingIdGenerator);
              if (commitResult instanceof PostingCommitResult.Rejected rejected) {
                throw new IllegalStateException(
                    "Generated interim-result-sweep posting failed bookkeeping acceptance: "
                        + rejected.rejection());
              }
              sweepPostingIds.add(
                  ((PostingCommitResult.Committed) commitResult).postingFact().postingId());
            }
            SweptInterimResult sweptInterimResult =
                new SweptInterimResult(
                    transferredPeriodResults.size() + 1,
                    interimResultSweepDraft.reportingPeriod(),
                    interimResultSweepDraft.resultHoldingAccountCode(),
                    interimResultSweepDraft.sweptTotals(),
                    interimResultSweepDraft.sweptAt(),
                    sweepPostingIds);
            transferredPeriodResults.add(sweptInterimResult);
            committed = true;
            return new InterimResultSweepOutcome.Transferred(sweptInterimResult);
          } finally {
            if (!committed) {
              restoreSnapshot(rollbackSnapshot);
            }
          }
        });
  }

  @Override
  public FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(closedAt, "closedAt");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new FiscalYearCloseOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          Optional<BookkeepingAdministrationRejection> closeWindowRejection =
              planner.closeHorizonRejection(reportingPeriod, bookIdentity, currentUtcDate);
          if (closeWindowRejection.isPresent()) {
            return rejectedFiscalYearClose(closeWindowRejection.orElseThrow());
          }
          Optional<ClosedFiscalYearRecord> existingClose = existingClose(reportingPeriod);
          if (existingClose.isPresent()) {
            return new FiscalYearCloseOutcome.Closed(existingClose.orElseThrow(), true);
          }
          Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
              planner.closeHorizonRejection(
                  reportingPeriod, bookIdentity, currentUtcDate, transferredThroughEffectiveDate());
          if (closeHorizonRejection.isPresent()) {
            return rejectedFiscalYearClose(closeHorizonRejection.orElseThrow());
          }
          List<RegisteredAccount> accounts = sortedAccounts();
          CloseTargets closeTargets = resolveCloseTargets(planner, bookIdentity, accounts);
          if (closeTargets.rejection() != null) {
            return rejectedFiscalYearClose(closeTargets.rejection());
          }
          FiscalYearCloseDraft closeDraft =
              planner.closeDraft(
                  reportingPeriod,
                  bookIdentity,
                  closeTargets.requiredCapitalAccount(),
                  closeTargets.requiredResultHoldingAccount(),
                  closeTargets.requiredRetainedAccumulatedAccount(),
                  accounts,
                  postings(reportingPeriod.effectiveDateRange()),
                  latestTransferredThroughWithinPeriod(reportingPeriod),
                  closedAt);
          return persistFiscalYearClose(closeDraft, postingIdGenerator);
        });
  }

  private Optional<ClosedFiscalYearRecord> existingClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod) {
    return closedFiscalYears.stream()
        .filter(closedFiscalYear -> closedFiscalYear.reportingPeriod().equals(reportingPeriod))
        .findFirst();
  }

  private List<RegisteredAccount> sortedAccounts() {
    return accountsByCode.values().stream()
        .sorted(java.util.Comparator.comparing(account -> account.accountCode().value()))
        .toList();
  }

  private CloseTargets resolveCloseTargets(
      FiscalYearClosePlanner planner, BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    CloseTargetSelectionResult capitalSelection =
        closeTargetSelectionResult(planner.capitalAccount(accounts));
    if (capitalSelection.rejection() != null) {
      return CloseTargets.rejected(capitalSelection.rejection());
    }
    CloseTargetSelectionResult resultHoldingSelection =
        closeTargetSelectionResult(planner.resultHoldingAccount(bookIdentity, accounts));
    if (resultHoldingSelection.rejection() != null) {
      return CloseTargets.rejected(resultHoldingSelection.rejection());
    }
    CloseTargetSelectionResult retainedAccumulatedSelection =
        closeTargetSelectionResult(planner.retainedAccumulatedAccount(accounts));
    if (retainedAccumulatedSelection.rejection() != null) {
      return CloseTargets.rejected(retainedAccumulatedSelection.rejection());
    }
    return CloseTargets.accepted(
        capitalSelection.requiredAccount(),
        resultHoldingSelection.requiredAccount(),
        retainedAccumulatedSelection.requiredAccount());
  }

  private FiscalYearCloseOutcome persistFiscalYearClose(
      FiscalYearCloseDraft closeDraft, PostingIdGenerator postingIdGenerator) {
    InMemoryBookSessionSnapshot rollbackSnapshot = snapshotState();
    boolean committed = false;
    try {
      if (closeDraft.unsweptInterimResultSweepDraft() != null) {
        InterimResultSweepOutcome sweepOutcome =
            interimResultSweep(closeDraft.unsweptInterimResultSweepDraft(), postingIdGenerator);
        if (!(sweepOutcome instanceof InterimResultSweepOutcome.Transferred)) {
          throw new IllegalStateException(
              "Generated interim-result sweep failed during fiscal-year close.");
        }
      }
      ClosedFiscalYearRecord closedFiscalYear =
          persistFiscalYearCloseRecord(closeDraft, postingIdGenerator);
      committed = true;
      return new FiscalYearCloseOutcome.Closed(closedFiscalYear, false);
    } finally {
      if (!committed) {
        restoreSnapshot(rollbackSnapshot);
      }
    }
  }

  private static FiscalYearCloseOutcome.Rejected rejectedFiscalYearClose(
      BookkeepingAdministrationRejection rejection) {
    return new FiscalYearCloseOutcome.Rejected(rejection);
  }

  private static CloseTargetSelectionResult closeTargetSelectionResult(
      CloseTargetSelection selection) {
    return switch (selection) {
      case AcceptedCloseTargetSelection accepted ->
          new CloseTargetSelectionResult(accepted.account(), null);
      case RejectedCloseTargetSelection rejected ->
          new CloseTargetSelectionResult(null, rejected.rejection());
    };
  }

  private ClosedFiscalYearRecord persistFiscalYearCloseRecord(
      FiscalYearCloseDraft closeDraft, PostingIdGenerator postingIdGenerator) {
    List<PostingId> closePostingIds = new ArrayList<>();
    for (dev.erst.fingrind.executor.spi.PostingDraft closePostingDraft :
        closeDraft.closePostingDrafts()) {
      PostingCommitResult commitResult = commit(closePostingDraft, postingIdGenerator);
      if (commitResult instanceof PostingCommitResult.Rejected rejected) {
        throw new IllegalStateException(
            "Generated fiscal-year-close posting failed bookkeeping acceptance: "
                + rejected.rejection());
      }
      closePostingIds.add(((PostingCommitResult.Committed) commitResult).postingFact().postingId());
    }
    ClosedFiscalYearRecord closedFiscalYear =
        new ClosedFiscalYearRecord(
            closedFiscalYears.size() + 1,
            closeDraft.reportingPeriod(),
            closeDraft.capitalAccountCode(),
            closeDraft.resultHoldingAccountCode(),
            closeDraft.retainedAccumulatedAccountCode(),
            closeDraft.closedAt(),
            closePostingIds);
    closedFiscalYears.add(closedFiscalYear);
    return closedFiscalYear;
  }

  private record CloseTargetSelectionResult(
      @Nullable RegisteredAccount account, @Nullable BookkeepingAdministrationRejection rejection) {
    private RegisteredAccount requiredAccount() {
      return Objects.requireNonNull(account, "account");
    }
  }

  private record CloseTargets(
      @Nullable RegisteredAccount capitalAccount,
      @Nullable RegisteredAccount resultHoldingAccount,
      @Nullable RegisteredAccount retainedAccumulatedAccount,
      @Nullable BookkeepingAdministrationRejection rejection) {
    private static CloseTargets accepted(
        RegisteredAccount capitalAccount,
        RegisteredAccount resultHoldingAccount,
        RegisteredAccount retainedAccumulatedAccount) {
      return new CloseTargets(
          capitalAccount, resultHoldingAccount, retainedAccumulatedAccount, null);
    }

    private static CloseTargets rejected(BookkeepingAdministrationRejection rejection) {
      return new CloseTargets(null, null, null, rejection);
    }

    private RegisteredAccount requiredCapitalAccount() {
      return Objects.requireNonNull(capitalAccount, "capitalAccount");
    }

    private RegisteredAccount requiredResultHoldingAccount() {
      return Objects.requireNonNull(resultHoldingAccount, "resultHoldingAccount");
    }

    private RegisteredAccount requiredRetainedAccumulatedAccount() {
      return Objects.requireNonNull(retainedAccumulatedAccount, "retainedAccumulatedAccount");
    }
  }

  private Optional<LocalDate> latestTransferredThroughWithinPeriod(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod) {
    return transferredPeriodResults.stream()
        .map(SweptInterimResult::reportingPeriod)
        .filter(
            transferredPeriod ->
                !transferredPeriod.effectiveDateFrom().isBefore(reportingPeriod.effectiveDateFrom())
                    && !transferredPeriod
                        .effectiveDateTo()
                        .isAfter(reportingPeriod.effectiveDateTo()))
        .map(dev.erst.fingrind.core.ReportingPeriod::effectiveDateTo)
        .max(Comparator.naturalOrder());
  }

  protected final InMemoryBookSessionSnapshot snapshotState() {
    return new InMemoryBookSessionSnapshot(
        initialized,
        initializedAt,
        bookIdentity,
        Map.copyOf(accountsByCode),
        Map.copyOf(taxRegistrationsById),
        Map.copyOf(postingsByIdempotencyKey),
        Map.copyOf(postingsByPostingId),
        inventoryMovementsByPostingId.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))),
        Map.copyOf(inventoryStateByAccount),
        Map.copyOf(reversalsByPriorPostingId),
        List.copyOf(transferredPeriodResults),
        List.copyOf(closedFiscalYears));
  }

  protected final void restoreSnapshot(InMemoryBookSessionSnapshot snapshot) {
    initialized = snapshot.initialized();
    initializedAt = snapshot.initializedAt();
    bookIdentity = snapshot.bookIdentity();
    accountsByCode.clear();
    accountsByCode.putAll(snapshot.accountsByCode());
    taxRegistrationsById.clear();
    taxRegistrationsById.putAll(snapshot.taxRegistrationsById());
    postingsByIdempotencyKey.clear();
    postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
    postingsByPostingId.clear();
    postingsByPostingId.putAll(snapshot.postingsByPostingId());
    inventoryMovementsByPostingId.clear();
    inventoryMovementsByPostingId.putAll(snapshot.inventoryMovementsByPostingId());
    inventoryStateByAccount.clear();
    inventoryStateByAccount.putAll(snapshot.inventoryStateByAccount());
    reversalsByPriorPostingId.clear();
    reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
    transferredPeriodResults.clear();
    transferredPeriodResults.addAll(snapshot.transferredPeriodResults());
    closedFiscalYears.clear();
    closedFiscalYears.addAll(snapshot.closedFiscalYears());
  }
}
