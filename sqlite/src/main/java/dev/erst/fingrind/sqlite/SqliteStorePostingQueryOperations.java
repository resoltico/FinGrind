package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationOperationCommitment;
import dev.erst.fingrind.core.attestation.AttestationPostingCommitmentInspection;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Posting-history and close-horizon reads over one SQLite-backed book session. */
final class SqliteStorePostingQueryOperations {
  /** One initialized-book posting query executed against a live SQLite handle. */
  @FunctionalInterface
  private interface NativeQuery<T> {
    /** Runs one posting query against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStorePostingQueryOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneStoredRequestPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
                    statement -> statement.bindText(1, idempotencyKey.value())));
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(postingId, "postingId");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneCommittedPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_ID,
                    statement -> statement.bindText(1, postingId.value())));
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(priorPostingId, "priorPostingId");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneCommittedPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_REVERSAL_FOR,
                    statement -> statement.bindText(1, priorPostingId.value())));
  }

  Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds) {
    lifecycle.ensureOpenSession();
    Set<PostingId> checkedPostingIds = Set.copyOf(Objects.requireNonNull(postingIds, "postingIds"));
    if (checkedPostingIds.isEmpty()) {
      return Map.of();
    }
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> {
          AttestationPostingCommitmentInspection inspection;
          try {
            inspection =
                AttestationVerifier.verifyAndInspectPostingCommitments(
                    SqliteAttestationEvidenceStore.loadAll(activeDatabase));
          } catch (AttestationVerificationException exception) {
            throw new SqliteProtectedBookVerificationException(exception);
          }
          Map<UUID, AttestationOperationCommitment> commitmentsByPostingId =
              inspection.commitmentsByPostingId();
          Map<PostingId, AttestationCommit> commitments = new ConcurrentHashMap<>();
          for (PostingId postingId : checkedPostingIds) {
            AttestationOperationCommitment commitment =
                commitmentsByPostingId.get(UUID.fromString(postingId.value()));
            if (commitment != null) {
              commitments.put(postingId, attestationCommit(commitment));
            }
          }
          return Map.copyOf(commitments);
        });
  }

  private static AttestationCommit attestationCommit(AttestationOperationCommitment commitment) {
    return new AttestationCommit(commitment.operationOrder(), commitment.operationHeadHex());
  }

  PostingHistoryPage listPostings(PostingHistoryQuery query) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(query, "query");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> context.postingReader().loadPostingPage(activeDatabase, query));
  }

  List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    lifecycle.ensureOpenSession();
    EffectiveDateRange range = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .loadCommittedPostings(
                    activeDatabase,
                    SqlitePostingSql.LOAD_POSTINGS_IN_RANGE,
                    statement -> bindEffectiveDateRange(statement, range)));
  }

  Optional<LocalDate> earliestPostingEffectiveDate() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadOptionalText(
                    activeDatabase,
                    SqliteReportingPeriodCloseSql.FIND_EARLIEST_POSTING_EFFECTIVE_DATE,
                    statement -> {})
                .map(
                    text ->
                        CanonicalTemporalText.parseLocalDate(text, "postingFact.effectiveDate")));
  }

  Optional<LocalDate> transferredThroughEffectiveDate() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadOptionalText(
                    activeDatabase,
                    SqliteReportingPeriodCloseSql.FIND_CLOSED_THROUGH_EFFECTIVE_DATE,
                    statement -> {})
                .map(
                    text ->
                        CanonicalTemporalText.parseLocalDate(
                            text, "interimResultSweep.effectiveDateTo")));
  }

  private <T> T queryInitialized(String failureMessage, NativeQuery<T> query) {
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> query.run(lifecycle.initializedQueryDatabase()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }

  private static void bindEffectiveDateRange(
      SqliteNativeStatement statement, EffectiveDateRange effectiveDateRange) {
    String effectiveDateFrom =
        effectiveDateRange.effectiveDateFrom().map(LocalDate::toString).orElse(null);
    String effectiveDateTo =
        effectiveDateRange.effectiveDateTo().map(LocalDate::toString).orElse(null);
    statement.bindText(1, effectiveDateFrom);
    statement.bindText(2, effectiveDateFrom);
    statement.bindText(3, effectiveDateTo);
    statement.bindText(4, effectiveDateTo);
  }
}
