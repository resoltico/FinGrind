package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.PostingFact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared SQLite-specific assertions for Jazzer harnesses. */
public final class SqliteFuzzAssertions {
  private static final String TEST_BOOK_KEY = "fingrind-jazzer-book-key";

  private SqliteFuzzAssertions() {}

  /** Asserts that a committed FinGrind book file uses the canonical strict-table schema. */
  public static void assertCommittedBookUsesStrictTables(Path bookPath) {
    try (SqliteBookPassphrase passphrase = bookPassphrase()) {
      SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase);
      try {
        assertQueryInt(
            database,
            "select strict from pragma_table_list('book_meta') where name = 'book_meta'",
            1);
        assertQueryInt(
            database,
            "select strict from pragma_table_list('account') where name = 'account'",
            1);
        assertQueryInt(
            database,
            "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'",
            1);
        assertQueryInt(
            database,
            "select strict from pragma_table_list('journal_line') where name = 'journal_line'",
            1);
        assertQueryInt(
            database,
            "select count(*) from book_meta where key = 'initialized_at'",
            1);
        assertQueryInt(
            database,
            """
            select count(*)
            from pragma_foreign_key_list('journal_line')
            where "table" = 'account'
              and "from" = 'account_code'
              and "to" = 'account_code'
            """,
            1);
      } finally {
        database.close();
      }
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "Committed SQLite book did not satisfy the strict-schema invariant.", exception);
    }
  }

  /** Deactivates one account directly in SQLite so harnesses can assert reactivation. */
  public static void deactivateAccount(Path bookPath, String accountCode)
      throws java.io.IOException {
    updateAccountActivity(bookPath, accountCode, 0);
  }

  /** Activates one account directly in SQLite for deterministic harness setup. */
  public static void activateAccount(Path bookPath, String accountCode) throws java.io.IOException {
    updateAccountActivity(bookPath, accountCode, 1);
  }

  private static void updateAccountActivity(Path bookPath, String accountCode, int activeFlag)
      throws java.io.IOException {
    if (!Files.exists(bookPath)) {
      throw new IllegalArgumentException("SQLite book does not exist: " + bookPath);
    }
    try (SqliteBookPassphrase passphrase = bookPassphrase()) {
      SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase);
      try {
        database.executeStatement(
            """
            update account
               set active = %d
             where account_code = '%s'
            """
                .formatted(activeFlag, escapeSqlLiteral(accountCode)));
      } finally {
        database.close();
      }
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException("Failed to update account active flag for SQLite fuzz setup.", exception);
    }
  }

  /** Builds deterministic protected-book passphrase material for one fuzz or replay command. */
  public static SqliteBookPassphrase bookPassphrase() {
    return SqliteBookPassphrase.fromCharacters(
        "jazzer deterministic book passphrase", TEST_BOOK_KEY.toCharArray());
  }

  /** Opens one deterministic protected-book store for fuzz and replay flows. */
  public static SqlitePostingFactStore openStore(Path bookPath) {
    return new SqlitePostingFactStore(bookPath, bookPassphrase());
  }

  /** Asserts that one open store connection keeps FinGrind's connection-hardening pragmas. */
  public static void assertStoreConnectionHardening(SqlitePostingFactStore postingFactStore) {
    try {
      SqliteNativeDatabase database = postingFactStore.activeNativeDatabase();
      assertQueryInt(database, "pragma foreign_keys", 1);
      assertQueryText(database, "pragma journal_mode", "delete");
      assertQueryInt(database, "pragma synchronous", 3);
      assertQueryInt(database, "pragma trusted_schema", 0);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "SQLite store connection did not satisfy the pragma-hardening invariant.", exception);
    }
  }

  private static void assertQueryInt(SqliteNativeDatabase database, String sql, int expectedValue)
      {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      int actualValue = statement.columnInt(0);
      if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (actualValue != expectedValue) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  private static void assertQueryText(
      SqliteNativeDatabase database, String sql, String expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      String actualValue = statement.columnText(0);
      if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (!expectedValue.equalsIgnoreCase(actualValue)) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  private static String escapeSqlLiteral(String text) {
    return text.replace("'", "''");
  }
}
