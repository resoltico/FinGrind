package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;

/** SQLite book and connection assertions shared by Jazzer harnesses. */
public final class SqliteFuzzBookAssertions {
  private static final String TEST_BOOK_KEY = "fingrind-jazzer-book-key";

  private SqliteFuzzBookAssertions() {}

  /** Asserts that a committed FinGrind book file uses the canonical strict-table schema. */
  public static void assertCommittedBookUsesStrictTables(Path bookPath) {
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      SqliteFuzzQueryAssertions.assertQueryInt(
          database,
          "select strict from pragma_table_list('book_meta') where name = 'book_meta'",
          1);
      SqliteFuzzQueryAssertions.assertQueryInt(
          database, "select strict from pragma_table_list('account') where name = 'account'", 1);
      SqliteFuzzQueryAssertions.assertQueryInt(
          database,
          "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'",
          1);
      SqliteFuzzQueryAssertions.assertQueryInt(
          database,
          "select strict from pragma_table_list('journal_line') where name = 'journal_line'",
          1);
      SqliteFuzzQueryAssertions.assertQueryInt(
          database, "select count(*) from book_meta where meta_key = 'initialized_at'", 1);
      SqliteFuzzQueryAssertions.assertQueryInt(
          database,
          """
          select count(*)
          from pragma_foreign_key_list('journal_line')
          where "table" = 'account'
            and "from" = 'account_code'
            and "to" = 'account_code'
          """,
          1);
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

  /** Builds deterministic protected-book passphrase material for one fuzz or replay command. */
  public static SqliteBookPassphrase bookPassphrase() {
    return SqliteBookPassphrase.fromCharacters(
        "jazzer deterministic book passphrase", TEST_BOOK_KEY.toCharArray());
  }

  /** Opens one deterministic protected-book store for fuzz and replay flows. */
  public static SqlitePostingSession openStore(Path bookPath) {
    return SqlitePostingSessions.open(bookPath, bookPassphrase());
  }

  /** Asserts that one open store connection keeps FinGrind's connection-hardening pragmas. */
  public static void assertStoreConnectionHardening(AutoCloseable postingSurface) {
    try {
      SqliteNativeDatabase database =
          SqliteFuzzQueryAssertions.requireOwnedStore(postingSurface).activeNativeDatabase();
      SqliteFuzzQueryAssertions.assertQueryInt(database, "pragma foreign_keys", 1);
      SqliteFuzzQueryAssertions.assertQueryText(database, "pragma journal_mode", "delete");
      SqliteFuzzQueryAssertions.assertQueryInt(database, "pragma synchronous", 3);
      SqliteFuzzQueryAssertions.assertQueryInt(database, "pragma trusted_schema", 0);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "SQLite store connection did not satisfy the pragma-hardening invariant.", exception);
    }
  }

  private static void updateAccountActivity(Path bookPath, String accountCode, int activeFlag)
      throws java.io.IOException {
    if (!Files.exists(bookPath)) {
      throw new IllegalArgumentException("SQLite book does not exist: " + bookPath);
    }
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      database.executeStatement(
          """
          update account
             set active = %d
           where account_code = '%s'
          """
              .formatted(activeFlag, SqliteFuzzQueryAssertions.escapeSqlLiteral(accountCode)));
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "Failed to update account active flag for SQLite fuzz setup.", exception);
    }
  }
}
