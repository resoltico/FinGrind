package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared in-memory posting and close-horizon fixture state for executor tests. */
abstract class AbstractInMemoryPostingSession extends AbstractInMemoryBookAdministrationSession
    implements PostingValidationStore, PostingCommitStore, PeriodResultTransferStore {
  protected final Map<IdempotencyKey, CommittedPosting> postingsByIdempotencyKey =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> postingsByPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> reversalsByPriorPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final List<TransferredPeriodResult> transferredPeriodResults = new ArrayList<>();
  protected final PostingAcceptancePolicy postingAcceptancePolicy =
      PostingAcceptancePolicy.currentKernel();

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
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
                .map(
                    transferredPeriodResult ->
                        transferredPeriodResult.reportingPeriod().effectiveDateTo())
                .max(Comparator.naturalOrder()));
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          Optional<BookkeepingPostingRejection> rejection =
              postingAcceptancePolicy.rejectionFor(postingDraft, this);
          if (rejection.isPresent()) {
            return new PostingCommitResult.Rejected(rejection.orElseThrow());
          }
          CommittedPosting postingFact =
              postingDraft.materialize(postingIdGenerator.nextPostingId());
          IdempotencyKey idempotencyKey =
              postingFact.provenance().requestProvenance().idempotencyKey();
          CommittedPosting existingPosting =
              postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
          if (existingPosting != null) {
            return new PostingCommitResult.Rejected(
                new BookkeepingPostingRejection.DuplicateIdempotencyKey());
          }
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
              postingsByIdempotencyKey.remove(idempotencyKey, postingFact);
              postingsByPostingId.remove(postingFact.postingId(), postingFact);
              return new PostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.ReversalAlreadyExists(priorPostingId));
            }
          }
          return new PostingCommitResult.Committed(postingFact);
        });
  }

  /** Fixture helper that commits one fully materialized posting with its predefined posting id. */
  protected PostingCommitResult commit(CommittedPosting postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(),
            postingFact.postingLineage(),
            postingFact.postingKind(),
            postingFact.postingOriginKind(),
            postingFact.evidence(),
            postingFact.provenance()),
        postingFact::postingId);
  }

  @Override
  public PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(periodResultTransferDraft, "periodResultTransferDraft");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new PeriodResultTransferOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }

          InMemoryBookSessionSnapshot rollbackSnapshot = snapshotState();
          List<PostingId> transferPostingIds = new ArrayList<>();
          boolean committed = false;
          try {
            for (PostingDraft closingPostingDraft : periodResultTransferDraft.closingPostings()) {
              PostingCommitResult commitResult = commit(closingPostingDraft, postingIdGenerator);
              if (commitResult instanceof PostingCommitResult.Rejected rejected) {
                throw new IllegalStateException(
                    "Generated period-result-transfer posting failed bookkeeping acceptance: "
                        + rejected.rejection());
              }
              transferPostingIds.add(
                  ((PostingCommitResult.Committed) commitResult).postingFact().postingId());
            }
            TransferredPeriodResult transferredPeriodResult =
                new TransferredPeriodResult(
                    transferredPeriodResults.size() + 1,
                    periodResultTransferDraft.reportingPeriod(),
                    periodResultTransferDraft.resultHoldingAccountCode(),
                    periodResultTransferDraft.transferredTotals(),
                    periodResultTransferDraft.transferredAt(),
                    transferPostingIds);
            transferredPeriodResults.add(transferredPeriodResult);
            committed = true;
            return new PeriodResultTransferOutcome.Transferred(transferredPeriodResult);
          } finally {
            if (!committed) {
              restoreSnapshot(rollbackSnapshot);
            }
          }
        });
  }

  protected final InMemoryBookSessionSnapshot snapshotState() {
    return new InMemoryBookSessionSnapshot(
        initialized,
        initializedAt,
        bookIdentity,
        Map.copyOf(accountsByCode),
        Map.copyOf(postingsByIdempotencyKey),
        Map.copyOf(postingsByPostingId),
        Map.copyOf(reversalsByPriorPostingId),
        List.copyOf(transferredPeriodResults));
  }

  protected final void restoreSnapshot(InMemoryBookSessionSnapshot snapshot) {
    initialized = snapshot.initialized();
    initializedAt = snapshot.initializedAt();
    bookIdentity = snapshot.bookIdentity();
    accountsByCode.clear();
    accountsByCode.putAll(snapshot.accountsByCode());
    postingsByIdempotencyKey.clear();
    postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
    postingsByPostingId.clear();
    postingsByPostingId.putAll(snapshot.postingsByPostingId());
    reversalsByPriorPostingId.clear();
    reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
    transferredPeriodResults.clear();
    transferredPeriodResults.addAll(snapshot.transferredPeriodResults());
  }
}
