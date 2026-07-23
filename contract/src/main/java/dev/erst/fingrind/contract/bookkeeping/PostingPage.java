package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One stable ordered page of committed postings. */
public record PostingPage(
    BookIdentity bookIdentity,
    Optional<AccountCode> accountCodeFilter,
    EffectiveDateRange effectiveDateRange,
    List<PostingFact> postings,
    int limit,
    Optional<PostingPageCursor> nextCursor,
    Map<PostingId, PostingId> reversedByPostingIds,
    Map<PostingId, AttestationCommit> attestationCommitsByPostingId) {
  /** Validates one committed-posting page. */
  public PostingPage(
      BookIdentity bookIdentity,
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor,
      Map<PostingId, PostingId> reversedByPostingIds,
      Map<PostingId, AttestationCommit> attestationCommitsByPostingId) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.accountCodeFilter = Objects.requireNonNull(accountCodeFilter, "accountCodeFilter");
    this.effectiveDateRange = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    this.postings = ContractDescriptorValidation.copyList(postings, "postings");
    this.limit = limit;
    this.nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
    this.reversedByPostingIds =
        Map.copyOf(Objects.requireNonNull(reversedByPostingIds, "reversedByPostingIds"));
    this.attestationCommitsByPostingId =
        Map.copyOf(
            Objects.requireNonNull(attestationCommitsByPostingId, "attestationCommitsByPostingId"));
    if (limit < 1) {
      throw new IllegalArgumentException("Posting page limit must be greater than zero.");
    }
    Set<PostingId> postingIds = new HashSet<>();
    for (PostingFact posting : this.postings) {
      postingIds.add(posting.postingId());
    }
    if (!postingIds.containsAll(this.reversedByPostingIds.keySet())) {
      throw new IllegalArgumentException("Posting reversal links must belong to this page.");
    }
    if (!postingIds.containsAll(this.attestationCommitsByPostingId.keySet())) {
      throw new IllegalArgumentException(
          "Posting attestation commitments must belong to this page.");
    }
  }

  /** Returns whether another page exists after this one. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
