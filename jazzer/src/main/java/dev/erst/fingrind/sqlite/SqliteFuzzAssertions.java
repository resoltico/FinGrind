package dev.erst.fingrind.sqlite;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared SQLite-specific assertions for Jazzer harnesses. */
public final class SqliteFuzzAssertions {
  private SqliteFuzzAssertions() {}

  /** Asserts that a committed FinGrind book file uses the canonical strict-table schema. */
  public static void assertCommittedBookUsesStrictTables(Path bookPath) {
    try {
      SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
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

  /** Updates one account's active flag directly in SQLite so harnesses can assert reactivation. */
  public static void updateAccountActiveFlag(Path bookPath, String accountCode, boolean active)
      throws java.io.IOException {
    if (!Files.exists(bookPath)) {
      throw new IllegalArgumentException("SQLite book does not exist: " + bookPath);
    }
    try {
      SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
      try {
        database.executeStatement(
            """
            update account
               set active = %d
             where account_code = '%s'
            """
                .formatted(active ? 1 : 0, escapeSqlLiteral(accountCode)));
      } finally {
        database.close();
      }
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException("Failed to update account active flag for SQLite fuzz setup.", exception);
    }
  }

  /** Asserts that one open store connection keeps FinGrind's connection-hardening pragmas. */
  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  public static void assertStoreConnectionHardening(SqlitePostingFactStore postingFactStore) {
    try {
      Field databaseField = SqlitePostingFactStore.class.getDeclaredField("database");
      databaseField.setAccessible(true);
      SqliteNativeDatabase database = (SqliteNativeDatabase) databaseField.get(postingFactStore);
      if (database == null) {
        throw new IllegalStateException("SQLite store did not open a database handle.");
      }
      assertQueryInt(database, "pragma foreign_keys", 1);
      assertQueryInt(database, "pragma trusted_schema", 0);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "SQLite store connection did not satisfy the pragma-hardening invariant.", exception);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Failed to inspect the SQLite store connection for hardening checks.", exception);
    }
  }

  private static void assertQueryInt(SqliteNativeDatabase database, String sql, int expectedValue)
      throws SqliteNativeException {
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

  private static String escapeSqlLiteral(String text) {
    return text.replace("'", "''");
  }
}
