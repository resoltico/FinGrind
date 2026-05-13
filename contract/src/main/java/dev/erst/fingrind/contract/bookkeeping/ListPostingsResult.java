package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;

/** Closed result family for committed-posting page queries. */
public sealed interface ListPostingsResult
    permits ListPostingsResult.Listed, ListPostingsResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying one page of committed postings. */
  record Listed(PostingPage page) implements ListPostingsResult {
    /** Validates the committed-posting page payload. */
    public Listed {
      Objects.requireNonNull(page, "page");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(listedMapper, "listedMapper").apply(this);
    }
  }

  /** Deterministic refusal for committed-posting page queries. */
  record Rejected(BookQueryRejection rejection) implements ListPostingsResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
