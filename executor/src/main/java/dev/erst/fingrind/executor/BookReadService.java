package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;

/** Application entry point for inspection and the owned protected-book read capabilities. */
public final class BookReadService
    implements BookReadCatalogOperations,
        BookReadPostingOperations,
        BookReadStatementOperations,
        BookReadLifecycleOperations {
  private final BookkeepingReadStore bookStore;
  private final AttestationPostingCommitmentStore attestationCommitmentStore;
  private final BookkeepingReadService bookkeepingReadService;
  private final BookReadCatalogQueryOperations catalogQueries;
  private final BookReadPostingQueryOperations postingQueries;
  private final BookReadStatementQueryOperations statementQueries;
  private final BookReadLifecycleQueryOperations lifecycleQueries;

  /** Creates the read entry point with its inspection and owned query capabilities. */
  public BookReadService(
      BookkeepingReadStore bookStore,
      AttestationPostingCommitmentStore attestationCommitmentStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.attestationCommitmentStore =
        Objects.requireNonNull(attestationCommitmentStore, "attestationCommitmentStore");
    bookkeepingReadService = new BookkeepingReadService(this.bookStore);
    BookReportService reportService =
        new BookReportService(
            this.bookStore, this.attestationCommitmentStore, bookkeepingReadService);
    catalogQueries = new BookReadCatalogQueryOperations(bookkeepingReadService);
    postingQueries =
        new BookReadPostingQueryOperations(
            this.bookStore, this.attestationCommitmentStore, bookkeepingReadService);
    statementQueries = new BookReadStatementQueryOperations(reportService);
    lifecycleQueries = new BookReadLifecycleQueryOperations(reportService);
  }

  /** Inspects the selected book file without mutating it. */
  public BookInspection inspectBook() {
    return BookReadInspectionProjection.project(bookStore, bookkeepingReadService.inspectBook());
  }

  BookReadCatalogQueryOperations catalogQueries() {
    return catalogQueries;
  }

  BookReadPostingQueryOperations postingQueries() {
    return postingQueries;
  }

  BookReadStatementQueryOperations statementQueries() {
    return statementQueries;
  }

  BookReadLifecycleQueryOperations lifecycleQueries() {
    return lifecycleQueries;
  }
}
