package dev.erst.fingrind.contract;

import java.util.Objects;

/** Closed result family for committed-posting page queries. */
public sealed interface ListPostingsResult
    permits ListPostingsResult.Listed, ListPostingsResult.Rejected {

  /** Success result carrying one page of committed postings. */
  record Listed(PostingPage page) implements ListPostingsResult {
    /** Validates the committed-posting page payload. */
    public Listed {
      Objects.requireNonNull(page, "page");
    }
  }

  /** Deterministic refusal for committed-posting page queries. */
  record Rejected(BookQueryRejection rejection) implements ListPostingsResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
