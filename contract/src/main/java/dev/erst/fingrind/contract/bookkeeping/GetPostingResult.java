package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Closed result family for one committed-posting lookup. */
public sealed interface GetPostingResult permits GetPostingResult.Found, GetPostingResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Found, T> foundMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying the matching committed posting. */
  record Found(
      BookIdentity bookIdentity, PostingFact postingFact, Optional<PostingId> reversedByPostingId)
      implements GetPostingResult {
    /** Validates the committed-posting payload. */
    public Found {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(postingFact, "postingFact");
      Objects.requireNonNull(reversedByPostingId, "reversedByPostingId");
    }

    @Override
    public <T> T fold(Function<Found, T> foundMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(foundMapper, "foundMapper").apply(this);
    }
  }

  /** Deterministic refusal for committed-posting lookup. */
  record Rejected(BookQueryRejection rejection) implements GetPostingResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T> T fold(Function<Found, T> foundMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
