package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteAccountRegistryBehaviorTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void declareAccount_requiresInitializedBook() {
    Path databasePath = tempDirectory.resolve("declare-uninitialized.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void declareAccount_listsAndReactivatesAccounts() {
    Path databasePath = tempDirectory.resolve("declare-accounts.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      deactivateAccount(databasePath, "1000");
      assertEquals(
          new AccountDeclarationOutcome.Reactivated(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash main"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          List.of(
              declaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          listAccounts(postingFactStore));
    }
  }

  @Test
  void findAccount_returnsDeclaredAccountFromInitializedBook() {
    Path databasePath = tempDirectory.resolve("find-account.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          Optional.of(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void declareInventoryAccount_persistsAndProjectsUnitOfMeasure() {
    Path databasePath = tempDirectory.resolve("declare-inventory-account.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);

      AccountDeclarationOutcome.Declared declared =
          assertInstanceOf(
              AccountDeclarationOutcome.Declared.class,
              postingFactStore.declareAccount(
                  new AccountDeclaration(
                      new AccountCode("1400"),
                      new AccountName("Inventory"),
                      AccountType.ASSET,
                      financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                      new UnitOfMeasure("kg", 3)),
                  Instant.parse("2026-04-07T10:15:30Z"),
                  SqliteAttestationTestSupport.authorizer()));

      assertEquals(new UnitOfMeasure("kg", 3), declared.account().unitOfMeasure());
      assertEquals(
          Optional.of(new UnitOfMeasure("kg", 3)),
          listAccounts(postingFactStore).stream()
              .filter(account -> "1400".equals(account.accountCode().value()))
              .findFirst()
              .map(account -> account.unitOfMeasure()));
      assertEquals(
          "kg",
          queryText(
              requireStoreDatabase(postingFactStore),
              "select unit_of_measure from account where account_code = '1400'"));
      assertEquals(
          3,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select quantity_scale from account where account_code = '1400'"));
    }
  }

  @Test
  void declareAccount_rejectsAccountTaxonomyConflict() {
    Path databasePath = tempDirectory.resolve("declare-conflict.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                  new AccountCode("1000"),
                  accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
                  financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET))),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.ASSET,
              financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
              Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void declareAccount_rejectsAccountTypeConflict() {
    Path databasePath = tempDirectory.resolve("declare-account-type-conflict.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.AccountTypeConflict(
                  new AccountCode("1000"), AccountType.ASSET, AccountType.EXPENSE)),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              AccountType.EXPENSE,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void listAccounts_paginatesDeclaredRegistry() {
    Path databasePath = tempDirectory.resolve("list-accounts-paginated.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      declareAccount(
          postingFactStore,
          new AccountCode("2000"),
          new AccountName("Revenue"),
          dev.erst.fingrind.core.AccountType.REVENUE,
          NormalBalance.CREDIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      declareAccount(
          postingFactStore,
          new AccountCode("3000"),
          new AccountName("Receivable"),
          dev.erst.fingrind.core.AccountType.ASSET,
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          List.of(new AccountCode("1000"), new AccountCode("2000")),
          postingFactStore
              .listAccounts(new AccountRegistryQuery(2, Optional.empty()))
              .accounts()
              .stream()
              .map(RegisteredAccount::accountCode)
              .toList());
      var firstPage = postingFactStore.listAccounts(new AccountRegistryQuery(2, Optional.empty()));
      assertTrue(firstPage.hasMore());
      assertEquals(
          Optional.of(new AccountRegistryCursor(new AccountCode("2000"))), firstPage.nextCursor());
      assertEquals(
          List.of(new AccountCode("3000")),
          postingFactStore
              .listAccounts(new AccountRegistryQuery(2, firstPage.nextCursor()))
              .accounts()
              .stream()
              .map(RegisteredAccount::accountCode)
              .toList());
      assertFalse(
          postingFactStore
              .listAccounts(new AccountRegistryQuery(2, firstPage.nextCursor()))
              .hasMore());
    }
  }

  @Test
  void mutationWriterUpsertAccount_preservesImmutableTypeTaxonomyAndDeclarationTimestamp() {
    Path databasePath = tempDirectory.resolve("upsert-account-columns.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                staticBookAccess(databasePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  SqliteAccountRegistryMutationWriter.upsertAccount(
                      database,
                      registeredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash"),
                          dev.erst.fingrind.core.AccountType.ASSET,
                          NormalBalance.DEBIT,
                          true,
                          Instant.parse("2026-04-07T10:15:30Z")));
                  SqliteAccountRegistryMutationWriter.upsertAccount(
                      database,
                      registeredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash Renamed"),
                          dev.erst.fingrind.core.AccountType.REVENUE,
                          NormalBalance.CREDIT,
                          true,
                          Instant.parse("2026-04-07T10:15:30Z")));
                  assertEquals(
                      "Cash Renamed",
                      queryText(
                          database,
                          "select account_name from account where account_code = '1000'"));
                  assertEquals(
                      "CURRENT_ASSET",
                      queryText(
                          database,
                          "select financial_position_line_classification from account where account_code = '1000'"));
                  assertEquals(
                      "ASSET",
                      queryText(
                          database,
                          "select account_type from account where account_code = '1000'"));
                  assertEquals(
                      "2026-04-07T10:15:30Z",
                      queryText(
                          database, "select declared_at from account where account_code = '1000'"));
                }));
  }
}
