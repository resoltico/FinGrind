package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.BookReadService;

/** SQLite implementation of the book-inspection capability. */
interface SqliteCliInspectionReadOperations
    extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<BookInspection> inspectBook(BookAccess bookAccess) {
    return withBookRead(bookAccess, BookReadService::inspectBook);
  }
}
