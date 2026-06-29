package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One stable ordered page of committed postings. */
public record PostingPage(
    BookIdentity bookIdentity,
    Optional<AccountCode> accountCodeFilter,
    EffectiveDateRange effectiveDateRange,
    List<PostingFact> postings,
    int limit,
    Optional<PostingPageCursor> nextCursor,
    Map<PostingId, PostingId> reversedByPostingIds) {
  /** Validates one committed-posting page. */
  public PostingPage(
      BookIdentity bookIdentity,
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor,
      Map<PostingId, PostingId> reversedByPostingIds) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.accountCodeFilter = Objects.requireNonNull(accountCodeFilter, "accountCodeFilter");
    this.effectiveDateRange = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    this.postings = ContractDescriptorValidation.copyList(postings, "postings");
    this.limit = limit;
    this.nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
    this.reversedByPostingIds =
        Map.copyOf(Objects.requireNonNull(reversedByPostingIds, "reversedByPostingIds"));
    if (limit < 1) {
      throw new IllegalArgumentException("Posting page limit must be greater than zero.");
    }
  }

  /** Returns whether another page exists after this one. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
