package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared in-memory posting-validation and commit fixture state for executor tests. */
abstract class AbstractInMemoryPostingSession extends AbstractInMemoryBookAdministrationSession
    implements PostingValidationStore, PostingCommitStore {
  protected final Map<TaxRegistrationId, DeclaredTaxRegistration> taxRegistrationsById =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<IdempotencyKey, StoredRequestPosting> postingsByIdempotencyKey =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> postingsByPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, List<InventoryMovementRecord>> inventoryMovementsByPostingId =
      InMemoryBookSessionSupport.mutableMap();
  protected final Map<dev.erst.fingrind.core.AccountCode, InventoryAccountState>
      inventoryStateByAccount = InMemoryBookSessionSupport.mutableMap();
  protected final Map<PostingId, CommittedPosting> reversalsByPriorPostingId =
      InMemoryBookSessionSupport.mutableMap();
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
  public Optional<InventoryAccountState> findInventoryAccountState(
      dev.erst.fingrind.core.AccountCode inventoryAccountCode) {
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(inventoryStateByAccount.get(inventoryAccountCode)));
  }

  @Override
  public List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    return InMemoryBookSessionSupport.withLock(
        lock, () -> inventoryMovementsByPostingId.getOrDefault(postingId, List.of()));
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
                  accepted
                      .acceptedPosting()
                      .materialize(postingIdGenerator.nextPostingId(), postingDraft.provenance());
              Optional<dev.erst.fingrind.core.ReversalReference> reversalReference =
                  postingFact.postingLineage().reversalReference();
              if (reversalReference.isPresent()
                  && reversalsByPriorPostingId.containsKey(
                      reversalReference.orElseThrow().priorPostingId())) {
                yield new PostingCommitResult.Rejected(
                    new BookkeepingPostingRejection.ReversalAlreadyExists(
                        reversalReference.orElseThrow().priorPostingId()));
              }
              IdempotencyKey idempotencyKey =
                  postingFact.provenance().requestProvenance().idempotencyKey();
              postingsByIdempotencyKey.put(
                  idempotencyKey,
                  new StoredRequestPosting(postingFact, accepted.requestFingerprint()));
              postingsByPostingId.put(postingFact.postingId(), postingFact);
              if (reversalReference.isPresent()) {
                dev.erst.fingrind.core.ReversalReference postedReversal =
                    reversalReference.orElseThrow();
                reversalsByPriorPostingId.put(postedReversal.priorPostingId(), postingFact);
              }
              inventoryMovementsByPostingId.put(
                  postingFact.postingId(), accepted.acceptedPosting().inventoryMovements());
              inventoryStateByAccount.putAll(accepted.acceptedPosting().resultingInventoryStates());
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
}
