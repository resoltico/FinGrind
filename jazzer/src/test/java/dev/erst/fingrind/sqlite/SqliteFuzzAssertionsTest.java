package dev.erst.fingrind.sqlite;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.executor.BookAdministrationService;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers deterministic SQLite-specific assertions shared by Jazzer harnesses. */
class SqliteFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void sqliteAssertions_cover_happy_path_state_transitions_and_missing_files() throws Exception {
    Path bookPath = tempDirectory.resolve("entity-book.sqlite");
    var command = CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8));

    try (SqliteBookSession store = SqliteFuzzAssertions.openStore(bookPath)) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(store.administrationSession());
      CliFuzzFixtures.openBook(administrationService);
      java.util.List<DeclaredAccount> accounts =
          CliFuzzFixtures.declarePostingAccounts(administrationService, command);

      SqliteFuzzAssertions.assertStoreConnectionHardening(store);
      SqliteFuzzAssertions.deactivateAccount(bookPath, accounts.getFirst().accountCode().value());
      assertFalse(store.findAccount(accounts.getFirst().accountCode()).orElseThrow().active());
      SqliteFuzzAssertions.activateAccount(bookPath, accounts.getFirst().accountCode().value());
      assertTrue(store.findAccount(accounts.getFirst().accountCode()).orElseThrow().active());
    }

    SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(bookPath);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteFuzzAssertions.deactivateAccount(
                tempDirectory.resolve("missing.sqlite"), "1000"));
  }

  @Test
  void sqliteAssertions_reject_invalid_store_shapes_and_broken_schema_checks() throws Exception {
    Path bookPath = tempDirectory.resolve("entity-book.sqlite");
    try (SqliteBookSession store = SqliteFuzzAssertions.openStore(bookPath)) {
      Object database = activeNativeDatabase(store);

      IllegalStateException noRow =
          assertThrows(
              IllegalStateException.class,
              () -> invokePrivate("assertQueryInt", database, "select 1 where 1 = 0", 1));
      assertTrue(String.valueOf(noRow.getMessage()).contains("Expected one SQLite row"));

      IllegalStateException manyRows =
          assertThrows(
              IllegalStateException.class,
              () -> invokePrivate("assertQueryInt", database, "select 1 union all select 2", 1));
      assertTrue(String.valueOf(manyRows.getMessage()).contains("Expected one SQLite row only"));

      IllegalStateException wrongIntValue =
          assertThrows(
              IllegalStateException.class,
              () -> invokePrivate("assertQueryInt", database, "select 2", 1));
      assertTrue(
          String.valueOf(wrongIntValue.getMessage())
              .contains("Unexpected SQLite pragma/query value"));

      IllegalStateException wrongTextValue =
          assertThrows(
              IllegalStateException.class,
              () -> invokePrivate("assertQueryText", database, "select 'wal'", "delete"));
      assertTrue(
          String.valueOf(wrongTextValue.getMessage())
              .contains("Unexpected SQLite pragma/query value"));

      IllegalStateException noTextRow =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokePrivate("assertQueryText", database, "select 'wal' where 1 = 0", "delete"));
      assertTrue(String.valueOf(noTextRow.getMessage()).contains("Expected one SQLite row"));

      IllegalStateException manyTextRows =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokePrivate(
                      "assertQueryText", database, "select 'a' union all select 'b'", "a"));
      assertTrue(
          String.valueOf(manyTextRows.getMessage()).contains("Expected one SQLite row only"));
    }

    Path invalidBook = tempDirectory.resolve("invalid.sqlite");
    java.nio.file.Files.writeString(invalidBook, "not-a-book", UTF_8);
    IllegalStateException invalidSchema =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(invalidBook));
    assertTrue(String.valueOf(invalidSchema.getMessage()).contains("strict-schema invariant"));
    IllegalStateException invalidUpdate =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteFuzzAssertions.deactivateAccount(invalidBook, "1000"));
    assertTrue(
        String.valueOf(invalidUpdate.getMessage())
            .contains("Failed to update account active flag"));

    try (SqliteBookSession unsupportedSession =
        (SqliteBookSession)
            Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {SqliteBookSession.class},
                (proxy, method, args) -> null)) {
      IllegalArgumentException unsupported =
          assertThrows(
              IllegalArgumentException.class,
              () -> invokePrivate("requireStoreImplementation", unsupportedSession));
      assertTrue(
          String.valueOf(unsupported.getMessage())
              .contains("Unsupported SQLite book session implementation"));
    }
    assertEquals("a''b", invokePrivate("escapeSqlLiteral", "a'b"));
  }

  @Test
  void sqliteAssertions_wrap_connection_hardening_native_failures() throws Exception {
    Path bookPath = tempDirectory.resolve("synthetic-book.sqlite");
    try (SqlitePostingFactStore store =
        new SqlitePostingFactStore(bookPath, SqliteFuzzAssertions.bookPassphrase())) {
      injectActiveNativeDatabase(
          store,
          new ThrowingNativeDatabase(
              SqliteNativeBootstrap.api(),
              new SqliteNativeException(1, "synthetic prepare failure")));

      IllegalStateException hardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteFuzzAssertions.assertStoreConnectionHardening(store));

      assertTrue(
          String.valueOf(hardeningFailure.getMessage()).contains("pragma-hardening invariant"));
    }
  }

  private static Object activeNativeDatabase(SqliteBookSession store) throws Exception {
    Object storeImplementation = invokePrivate("requireStoreImplementation", store);
    Method method = storeImplementation.getClass().getDeclaredMethod("activeNativeDatabase");
    method.setAccessible(true);
    return method.invoke(storeImplementation);
  }

  private static Object invokePrivate(String methodName, Object... arguments) throws Exception {
    Method method =
        switch (methodName) {
          case "assertQueryInt" ->
              SqliteFuzzAssertions.class.getDeclaredMethod(
                  methodName, arguments[0].getClass(), String.class, int.class);
          case "assertQueryText" ->
              SqliteFuzzAssertions.class.getDeclaredMethod(
                  methodName, arguments[0].getClass(), String.class, String.class);
          case "requireStoreImplementation" ->
              SqliteFuzzAssertions.class.getDeclaredMethod(methodName, SqliteBookSession.class);
          case "escapeSqlLiteral" ->
              SqliteFuzzAssertions.class.getDeclaredMethod(methodName, String.class);
          default ->
              throw new IllegalArgumentException("Unsupported reflection target: " + methodName);
        };
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static void injectActiveNativeDatabase(
      SqlitePostingFactStore store, SqliteNativeDatabase database) throws Exception {
    Field lifecycleField = SqliteStoreContext.class.getDeclaredField("lifecycle");
    lifecycleField.setAccessible(true);
    Object lifecycle = lifecycleField.get(store);
    Field databaseField = lifecycle.getClass().getDeclaredField("database");
    databaseField.setAccessible(true);
    databaseField.set(lifecycle, new SqliteSessionDatabase(database));
  }

  private static String basicValidRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """;
  }

  private static final class ThrowingNativeDatabase extends SqliteNativeDatabase {
    private final SqliteNativeException failure;

    private ThrowingNativeDatabase(SqliteNativeApi sqliteApi, SqliteNativeException failure) {
      super(MemorySegment.NULL, sqliteApi);
      this.failure = failure;
    }

    @Override
    MemorySegment handle() {
      throw failure;
    }

    @Override
    public void close() {}
  }
}
