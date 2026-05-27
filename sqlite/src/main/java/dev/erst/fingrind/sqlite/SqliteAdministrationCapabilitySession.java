package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.List;

/** Administration-only wrapper over the shared SQLite store core. */
final class SqliteAdministrationCapabilitySession extends SqliteDelegatingSession
    implements SqliteAdministrationSession {
  SqliteAdministrationCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public BookLifecycleInspection inspectBook() {
    return store.inspectBook();
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return store.allAccounts();
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return store.listAccounts(query);
  }

  @Override
  public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    return store.openBook(initializedAt, bookIdentity);
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return store.declareAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
  }

  @Override
  public void close() {
    closeStore();
  }
}
