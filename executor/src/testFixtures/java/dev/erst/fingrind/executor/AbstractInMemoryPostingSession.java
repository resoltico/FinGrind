package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlan;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared in-memory posting and close-horizon fixture state for executor tests. */
abstract class AbstractInMemoryPostingSession extends AbstractInMemoryBookAdministrationSession
    implements PostingValidationStore, PostingCommitStore, ReportingPeriodCloseStore {
  protected final Map<TaxRegistrationId, DeclaredTaxRegistration> taxRegistrationsById =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<IdempotencyKey, StoredRequestPosting> postingsByIdempotencyKey =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> postingsByPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> reversalsByPriorPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final List<SweptInterimResult> transferredPeriodResults = new ArrayList<>();
  protected final List<ClosedFiscalYearRecord> closedFiscalYears = new ArrayList<>();
  protected final PostingAcceptancePolicy postingAcceptancePolicy =
      PostingAcceptancePolicy.currentKernel();

  @Override
  public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey)));
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(postingsByPostingId.get(postingId)));
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId)));
  }

  @Override
  public Optional<DeclaredTaxRegistration> findTaxRegistration(
      TaxRegistrationId taxRegistrationId) {
    Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(taxRegistrationsById.get(taxRegistrationId)));
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            postingsByPostingId.values().stream()
                .filter(
                    posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
                .sorted(
                    Comparator.comparing(
                            (CommittedPosting posting) -> posting.journalEntry().effectiveDate())
                        .thenComparing(posting -> posting.provenance().recordedAt())
                        .thenComparing(posting -> posting.postingId().value()))
                .toList());
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            postingsByPostingId.values().stream()
                .map(posting -> posting.journalEntry().effectiveDate())
                .min(Comparator.naturalOrder()));
  }

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
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          return switch (postingAcceptancePolicy.decisionFor(postingDraft, this)) {
            case PostingAcceptancePolicy.Decision.Replay replay ->
                new PostingCommitResult.Committed(replay.postingFact(), true);
            case PostingAcceptancePolicy.Decision.Rejected rejected ->
                new PostingCommitResult.Rejected(rejected.rejection());
            case PostingAcceptancePolicy.Decision.Accepted accepted -> {
              CommittedPosting postingFact =
                  postingDraft.materialize(postingIdGenerator.nextPostingId());
              IdempotencyKey idempotencyKey =
                  postingFact.provenance().requestProvenance().idempotencyKey();
              postingsByIdempotencyKey.put(
                  idempotencyKey,
                  new StoredRequestPosting(postingFact, accepted.requestFingerprint()));
              postingsByPostingId.put(postingFact.postingId(), postingFact);

              Optional<dev.erst.fingrind.core.ReversalReference> reversalReference =
                  postingFact.postingLineage().reversalReference();
              if (reversalReference.isPresent()) {
                dev.erst.fingrind.core.ReversalReference postedReversal =
                    reversalReference.orElseThrow();
                PostingId priorPostingId = postedReversal.priorPostingId();
                CommittedPosting existingReversal =
                    reversalsByPriorPostingId.putIfAbsent(priorPostingId, postingFact);
                if (existingReversal != null) {
                  postingsByIdempotencyKey.remove(idempotencyKey);
                  postingsByPostingId.remove(postingFact.postingId(), postingFact);
                  yield new PostingCommitResult.Rejected(
                      new BookkeepingPostingRejection.ReversalAlreadyExists(priorPostingId));
                }
              }
              yield new PostingCommitResult.Committed(postingFact, false);
            }
          };
        });
  }

  /** Fixture helper that commits one fully materialized posting with its predefined posting id. */
  protected PostingCommitResult commit(CommittedPosting postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        RequestFingerprintTestSupport.fingerprintedDraft(
            postingFact.journalEntry(),
            postingFact.postingLineage(),
            postingFact.postingKind(),
            postingFact.postingOriginKind(),
            postingFact.evidence(),
            postingFact.provenance()),
        postingFact::postingId);
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
            for (PostingDraft closingPostingDraft : interimResultSweepDraft.closingPostings()) {
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
          List<RegisteredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(java.util.Comparator.comparing(account -> account.accountCode().value()))
                  .toList();
          var capitalSelection = planner.capitalAccount(accounts);
          if (capitalSelection
              instanceof
              dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection rejected) {
            return new FiscalYearCloseOutcome.Rejected(rejected.rejection());
          }
          RegisteredAccount capitalAccount =
              ((dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection)
                      capitalSelection)
                  .account();
          var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
          if (resultHoldingSelection
              instanceof
              dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection rejected) {
            return new FiscalYearCloseOutcome.Rejected(rejected.rejection());
          }
          RegisteredAccount resultHoldingAccount =
              ((dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection)
                      resultHoldingSelection)
                  .account();
          var retainedAccumulatedSelection = planner.retainedAccumulatedAccount(accounts);
          if (retainedAccumulatedSelection
              instanceof
              dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection rejected) {
            return new FiscalYearCloseOutcome.Rejected(rejected.rejection());
          }
          RegisteredAccount retainedAccumulatedAccount =
              ((dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection)
                      retainedAccumulatedSelection)
                  .account();
          var closeHorizonRejection =
              planner.closeHorizonRejection(reportingPeriod, bookIdentity, currentUtcDate);
          if (closeHorizonRejection.isPresent()) {
            return new FiscalYearCloseOutcome.Rejected(closeHorizonRejection.orElseThrow());
          }
          FiscalYearCloseDraft closeDraft =
              planner.closeDraft(
                  reportingPeriod,
                  bookIdentity,
                  capitalAccount,
                  resultHoldingAccount,
                  retainedAccumulatedAccount,
                  accounts,
                  postings(reportingPeriod.effectiveDateRange()),
                  latestTransferredThroughWithinPeriod(reportingPeriod),
                  closedAt);
          InMemoryBookSessionSnapshot rollbackSnapshot = snapshotState();
          boolean committed = false;
          try {
            if (closeDraft.unsweptInterimResultSweepDraft() != null) {
              InterimResultSweepOutcome sweepOutcome =
                  interimResultSweep(
                      closeDraft.unsweptInterimResultSweepDraft(), postingIdGenerator);
              if (!(sweepOutcome instanceof InterimResultSweepOutcome.Transferred)) {
                throw new IllegalStateException(
                    "Generated interim-result sweep failed during fiscal-year close.");
              }
            }
            ClosedFiscalYearRecord closedFiscalYear =
                persistFiscalYearClose(closeDraft, postingIdGenerator);
            committed = true;
            return new FiscalYearCloseOutcome.Closed(closedFiscalYear);
          } finally {
            if (!committed) {
              restoreSnapshot(rollbackSnapshot);
            }
          }
        });
  }

  private ClosedFiscalYearRecord persistFiscalYearClose(
      FiscalYearCloseDraft closeDraft, PostingIdGenerator postingIdGenerator) {
    List<PostingId> closePostingIds = new ArrayList<>();
    for (PostingDraft closePostingDraft : closeDraft.closePostingDrafts()) {
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
    reversalsByPriorPostingId.clear();
    reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
    transferredPeriodResults.clear();
    transferredPeriodResults.addAll(snapshot.transferredPeriodResults());
    closedFiscalYears.clear();
    closedFiscalYears.addAll(snapshot.closedFiscalYears());
  }
}
