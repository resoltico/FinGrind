package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.PostingId;

/** SQLite implementation of committed-posting lookup and listing. */
interface SqliteCliPostingReadOperations
    extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<GetPostingResult> getPosting(
      BookAccess bookAccess, PostingId postingId) {
    return withBookRead(bookAccess, service -> service.getPosting(postingId));
  }

  @Override
  default ContractDecision<ListPostingsResult> listPostings(
      BookAccess bookAccess, ListPostingsQuery query) {
    return withBookRead(bookAccess, service -> service.listPostings(query));
  }
}
