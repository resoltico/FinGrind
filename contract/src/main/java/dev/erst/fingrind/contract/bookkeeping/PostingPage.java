package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stable ordered page of committed postings. */
public record PostingPage(
    List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
  /** Validates one committed-posting page. */
  public PostingPage(
      List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
    this.postings = ContractDescriptorValidation.copyList(postings, "postings");
    this.limit = limit;
    this.nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
    if (limit < 1) {
      throw new IllegalArgumentException("Posting page limit must be greater than zero.");
    }
  }

  /** Returns whether another page exists after this one. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
