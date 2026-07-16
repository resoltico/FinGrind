package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;

/** Catalog and account-balance read capability exposed by {@link BookReadService}. */
public sealed interface BookReadCatalogOperations permits BookReadService {
  /** Lists one paginated slice of the current account registry for the selected book. */
  default ListAccountsResult listAccounts(ListAccountsQuery query) {
    return ((BookReadService) this).catalogQueries().listAccounts(query);
  }

  /** Computes one grouped per-currency balance snapshot for the selected declared account. */
  default AccountBalanceResult accountBalance(AccountBalanceQuery query) {
    return ((BookReadService) this).catalogQueries().accountBalance(query);
  }
}
