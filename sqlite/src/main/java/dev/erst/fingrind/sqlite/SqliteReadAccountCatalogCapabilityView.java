package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared account-catalog and account-lookup defaults for SQLite read wrappers. */
interface SqliteReadAccountCatalogCapabilityView
    extends AccountLookupStore, AccountCatalogStore, SqliteLifecycleInspectionCapabilityView {
  @Override
  default Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountCatalog().findAccount(accountCode);
  }

  @Override
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountCatalog().findAccounts(accountCodes);
  }

  @Override
  default List<RegisteredAccount> allAccounts() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountCatalog().allAccounts();
  }

  @Override
  default AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountCatalog().listAccounts(query);
  }
}
