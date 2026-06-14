package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Account-catalog read surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreAccountCatalogView
    extends SqlitePostingFactStoreReadOperationsView {
  /** Returns lifecycle inspection facts for the protected book. */
  default BookLifecycleInspection inspectBook() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inspectBook();
  }

  /** Finds one registered account by account code. */
  default Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findAccount(accountCode);
  }

  /** Finds several registered accounts keyed by account code. */
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findAccounts(accountCodes);
  }

  /** Returns every registered account in declaration order. */
  default List<RegisteredAccount> allAccounts() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().allAccounts();
  }

  /** Returns one page of registered accounts for the supplied query. */
  default AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().listAccounts(query);
  }
}
