package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;

/** Reads ordered account-catalog views from the selected book. */
public interface AccountCatalogStore {
  /** Returns the declared accounts in one stable in-memory order for bookkeeping semantics. */
  List<RegisteredAccount> allAccounts();

  /** Returns one paginated slice of the declared account registry for one initialized book. */
  AccountRegistryPage listAccounts(AccountRegistryQuery query);
}
