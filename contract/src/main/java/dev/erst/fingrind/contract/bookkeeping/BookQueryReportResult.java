package dev.erst.fingrind.contract.bookkeeping;

import org.jspecify.annotations.Nullable;

/** Shared success-or-rejection grammar for book-query report results. */
public interface BookQueryReportResult<REPORTED> {
  /** Returns the reported value when the query succeeded. */
  @Nullable REPORTED reported();

  /** Returns the deterministic query rejection when the query did not succeed. */
  @Nullable BookQueryRejection rejection();
}
