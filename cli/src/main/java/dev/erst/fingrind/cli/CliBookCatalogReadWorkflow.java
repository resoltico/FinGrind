package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.core.PostingId;

/** Catalog and posting-history read capability over one protected book. */
interface CliBookCatalogReadWorkflow {
  /** Lists declared accounts with one cursor window. */
  ContractDecision<ListAccountsResult> listAccounts(BookAccess bookAccess, ListAccountsQuery query);

  /** Lists declared tax registrations with one cursor window. */
  ContractDecision<ListTaxRegistrationsResult> listTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query);

  /** Reads one posting by durable posting id. */
  ContractDecision<GetPostingResult> getPosting(BookAccess bookAccess, PostingId postingId);

  /** Lists postings with the selected filters and cursor window. */
  ContractDecision<ListPostingsResult> listPostings(BookAccess bookAccess, ListPostingsQuery query);
}
