package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** SQLite implementation of declared-account catalog reads. */
interface SqliteCliCatalogReadOperations
    extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<ListAccountsResult> listAccounts(
      BookAccess bookAccess, ListAccountsQuery query) {
    return withBookRead(bookAccess, service -> service.listAccounts(query));
  }
}
