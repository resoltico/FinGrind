package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** One stable ordered page of committed postings. */
public record PostingPage(List<PostingFact> postings, int limit, int offset, boolean hasMore) {
  /** Validates one committed-posting page. */
  public PostingPage {
    postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
    if (limit < 1) {
      throw new IllegalArgumentException("Posting page limit must be greater than zero.");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Posting page offset must not be negative.");
    }
  }
}
