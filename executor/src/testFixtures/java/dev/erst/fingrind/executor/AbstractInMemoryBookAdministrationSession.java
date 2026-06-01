package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** Shared in-memory lifecycle and account-registry fixture state for executor tests. */
abstract class AbstractInMemoryBookAdministrationSession
    implements BookAdministrationStore,
        BookLifecycleReader,
        AccountLookupStore,
        AccountCatalogStore {
  protected final ReentrantLock lock = new ReentrantLock();
  protected final Map<AccountCode, RegisteredAccount> accountsByCode =
      InMemoryBookSessionSupport.mutableMap();
  protected boolean initialized;
  protected Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
  protected BookIdentity bookIdentity =
      new BookIdentity(
          new EntityProfile(new BookEntityName("FinGrind Test Entity"), List.of()),
          dev.erst.fingrind.core.AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
          CurrencyUnit.of("USD"),
          new FiscalYearStart(1, 1));

  @Override
  public BookLifecycleInspection inspectBook() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new BookLifecycleInspection.Missing(BookFormatContract.FORMAT_VERSION);
          }
          return new BookLifecycleInspection.Initialized(
              BookFormatContract.APPLICATION_ID,
              BookFormatContract.FORMAT_VERSION,
              BookFormatContract.FORMAT_VERSION,
              initializedAt,
              bookIdentity);
        });
  }

  @Override
  public BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (initialized) {
            return new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookAlreadyInitialized());
          }
          initialized = true;
          this.initializedAt = initializedAt;
          this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
          return new BookOpeningOutcome.Opened(initializedAt, bookIdentity);
        });
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return InMemoryBookSessionSupport.withLock(
        lock, () -> Optional.ofNullable(accountsByCode.get(accountCode)));
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            accountCodes.stream()
                .filter(accountsByCode::containsKey)
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        accountCode -> accountCode, accountsByCode::get)));
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new AccountDeclarationOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          AccountDeclarationOutcome declarationOutcome =
              RegisteredAccount.declare(
                  accountsByCode.get(accountCode),
                  new AccountDeclaration(
                      accountCode, accountName, accountType, accountRole, accountTaxonomy),
                  declaredAt);
          if (declarationOutcome instanceof AccountDeclarationOutcome.Declared declared) {
            accountsByCode.put(accountCode, declared.account());
          }
          return declarationOutcome;
        });
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          List<RegisteredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(Comparator.comparing(account -> account.accountCode().value()))
                  .filter(
                      account ->
                          InMemoryBookSessionSupport.matchesAccountCursor(account, query.cursor()))
                  .toList();
          int end = Math.min(query.limit(), accounts.size());
          List<RegisteredAccount> pageItems = accounts.subList(0, end);
          return new AccountRegistryPage(
              pageItems,
              query.limit(),
              end < accounts.size()
                  ? Optional.of(new AccountRegistryCursor(pageItems.getLast().accountCode()))
                  : Optional.empty());
        });
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            accountsByCode.values().stream()
                .sorted(Comparator.comparing(account -> account.accountCode().value()))
                .toList());
  }

  /** Deactivates one declared account for fixture-driven tests. */
  protected void deactivateAccount(AccountCode accountCode) {
    InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          RegisteredAccount existingAccount = accountsByCode.get(accountCode);
          if (existingAccount == null) {
            throw new IllegalArgumentException("Account is not declared: " + accountCode.value());
          }
          accountsByCode.put(
              accountCode,
              new RegisteredAccount(
                  existingAccount.accountCode(),
                  existingAccount.accountName(),
                  existingAccount.accountType(),
                  existingAccount.accountRole(),
                  existingAccount.accountTaxonomy(),
                  false,
                  existingAccount.declaredAt()));
        });
  }
}
