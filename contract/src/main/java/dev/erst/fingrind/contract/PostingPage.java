package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** One stable ordered page of committed postings. */
public record PostingPage(
    List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
  /** Validates one committed-posting page. */
  public PostingPage(
      @Nullable List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
    this.postings = postings == null ? List.of() : List.copyOf(postings);
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
