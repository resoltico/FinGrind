package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
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
  private static final AttestationOperationAuthorizer TEST_SEED_AUTHORIZER =
      ignored -> {
        throw new AssertionError(
            "In-memory fixture seeding must not invoke an attestation signer.");
      };
  protected final ReentrantLock lock = new ReentrantLock();
  protected final Map<AccountCode, RegisteredAccount> accountsByCode =
      InMemoryBookSessionSupport.mutableMap();
  protected boolean initialized;
  protected Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
  protected BookIdentity bookIdentity =
      new BookIdentity(
          new EntityProfile(new BookEntityName("FinGrind Test Entity")),
          BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
          CurrencyUnit.of("USD"),
          new FiscalYearStart(1, 1),
          java.time.LocalDate.parse("2026-01-01"));

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

  /** Test-fixture-only account seeding that never represents a protected-book production write. */
  public AccountDeclarationOutcome declareAccount(
      AccountDeclaration declaration, Instant declaredAt) {
    return declareAccount(declaration, declaredAt, TEST_SEED_AUTHORIZER);
  }

  @Override
  public BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts) {
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
          Objects.requireNonNull(seededAccounts, "seededAccounts")
              .forEach(
                  declaration ->
                      accountsByCode.put(
                          declaration.accountCode(),
                          new RegisteredAccount(
                              declaration.accountCode(),
                              declaration.accountName(),
                              declaration.accountType(),
                              declaration.accountTaxonomy(),
                              declaration.unitOfMeasure(),
                              true,
                              initializedAt)));
          return new BookOpeningOutcome.Opened(initializedAt, bookIdentity);
        });
  }

  @Override
  public BookOpeningOutcome openAttestedBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<AccountDeclaration> seededAccounts,
      AttestationEvidence genesisEvidence) {
    AttestationGenesis.requireMatchingBookIdentity(genesisEvidence, bookIdentity);
    return openBook(initializedAt, bookIdentity, seededAccounts);
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
      AccountDeclaration declaration,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (!initialized) {
            return new AccountDeclarationOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          AccountDeclarationOutcome declarationOutcome =
              RegisteredAccount.declare(
                  accountsByCode.get(declaration.accountCode()), declaration, declaredAt);
          switch (declarationOutcome) {
            case AccountDeclarationOutcome.Declared declared ->
                accountsByCode.put(declaration.accountCode(), declared.account());
            case AccountDeclarationOutcome.Reactivated reactivated ->
                accountsByCode.put(declaration.accountCode(), reactivated.account());
            case AccountDeclarationOutcome.Renamed renamed ->
                accountsByCode.put(declaration.accountCode(), renamed.account());
            case AccountDeclarationOutcome.Unchanged _, AccountDeclarationOutcome.Rejected _ -> {}
          }
          return declarationOutcome;
        });
  }

  /** Convenience overload for test code that constructs one declaration inline. */
  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return declareAccount(
        new AccountDeclaration(accountCode, accountName, accountType, accountTaxonomy),
        declaredAt,
        attestationAuthorizer);
  }

  /** Test-fixture-only account seeding that never represents a protected-book production write. */
  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return declareAccount(
        accountCode, accountName, accountType, accountTaxonomy, declaredAt, TEST_SEED_AUTHORIZER);
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
                  existingAccount.accountTaxonomy(),
                  existingAccount.unitOfMeasure(),
                  false,
                  existingAccount.declaredAt()));
        });
  }
}
