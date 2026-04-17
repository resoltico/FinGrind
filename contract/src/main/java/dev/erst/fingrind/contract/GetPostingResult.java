package dev.erst.fingrind.contract;

import java.util.Objects;

/** Closed result family for one committed-posting lookup. */
public sealed interface GetPostingResult permits GetPostingResult.Found, GetPostingResult.Rejected {

  /** Success result carrying the matching committed posting. */
  record Found(PostingFact postingFact) implements GetPostingResult {
    /** Validates the committed-posting payload. */
    public Found {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Deterministic refusal for committed-posting lookup. */
  record Rejected(BookQueryRejection rejection) implements GetPostingResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
