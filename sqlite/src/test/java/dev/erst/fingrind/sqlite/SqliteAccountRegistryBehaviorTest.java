package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteAccountRegistryBehaviorTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void declareAccount_requiresInitializedBook() {
    Path databasePath = tempDirectory.resolve("declare-uninitialized.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void declareAccount_listsAndReactivatesAccounts() {
    Path databasePath = tempDirectory.resolve("declare-accounts.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));

      deactivateAccount(databasePath, "1000");

      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash main"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          List.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T10:15:30Z"))),
          listAccounts(postingFactStore));
    }
  }

  @Test
  void findAccount_returnsDeclaredAccountFromInitializedBook() {
    Path databasePath = tempDirectory.resolve("find-account.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      assertEquals(
          Optional.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void declareAccount_rejectsNormalBalanceConflict() {
    Path databasePath = tempDirectory.resolve("declare-conflict.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new DeclareAccountResult.Rejected(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void listAccounts_paginatesDeclaredRegistry() {
    Path databasePath = tempDirectory.resolve("list-accounts-paginated.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          NormalBalance.CREDIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("3000"),
          new AccountName("Receivable"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          List.of(new AccountCode("1000"), new AccountCode("2000")),
          postingFactStore
              .listAccounts(new ListAccountsQuery(2, Optional.empty()))
              .accounts()
              .stream()
              .map(DeclaredAccount::accountCode)
              .toList());
      var firstPage = postingFactStore.listAccounts(new ListAccountsQuery(2, Optional.empty()));
      assertTrue(firstPage.hasMore());
      assertEquals(
          Optional.of(new AccountPageCursor(new AccountCode("2000"))), firstPage.nextCursor());
      assertEquals(
          List.of(new AccountCode("3000")),
          postingFactStore
              .listAccounts(new ListAccountsQuery(2, firstPage.nextCursor()))
              .accounts()
              .stream()
              .map(DeclaredAccount::accountCode)
              .toList());
      assertFalse(
          postingFactStore
              .listAccounts(new ListAccountsQuery(2, firstPage.nextCursor()))
              .hasMore());
    }
  }

  @Test
  void mutationWriterUpsertAccount_preservesImmutableBalanceAndUpdatesRedeclarationTimestamp() {
    Path databasePath = tempDirectory.resolve("upsert-account-columns.sqlite");

    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                staticBookAccess(databasePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  SqliteMutationWriter.upsertAccount(
                      database,
                      new DeclaredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash"),
                          NormalBalance.DEBIT,
                          true,
                          Instant.parse("2026-04-07T10:15:30Z")));
                  SqliteMutationWriter.upsertAccount(
                      database,
                      new DeclaredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash Renamed"),
                          NormalBalance.CREDIT,
                          true,
                          Instant.parse("2026-04-08T10:15:30Z")));

                  assertEquals(
                      "Cash Renamed",
                      queryText(
                          database,
                          "select account_name from account where account_code = '1000'"));
                  assertEquals(
                      "DEBIT",
                      queryText(
                          database,
                          "select normal_balance from account where account_code = '1000'"));
                  assertEquals(
                      "2026-04-08T10:15:30Z",
                      queryText(
                          database, "select declared_at from account where account_code = '1000'"));
                }));
  }
}
