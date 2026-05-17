package dev.erst.fingrind.sqlite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Shared SQLite-specific assertions for Jazzer harnesses. */
public final class SqliteFuzzAssertions {
  private static final String TEST_BOOK_KEY = "fingrind-jazzer-book-key";

  private SqliteFuzzAssertions() {}

  /** Asserts that a committed FinGrind book file uses the canonical strict-table schema. */
  public static void assertCommittedBookUsesStrictTables(Path bookPath) {
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      assertQueryInt(
          database,
          "select strict from pragma_table_list('book_meta') where name = 'book_meta'",
          1);
      assertQueryInt(
          database, "select strict from pragma_table_list('account') where name = 'account'", 1);
      assertQueryInt(
          database,
          "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'",
          1);
      assertQueryInt(
          database,
          "select strict from pragma_table_list('journal_line') where name = 'journal_line'",
          1);
      assertQueryInt(database, "select count(*) from book_meta where key = 'initialized_at'", 1);
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
    try (SqliteBookPassphrase passphrase = bookPassphrase();
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      database.executeStatement(
          """
          update account
             set active = %d
           where account_code = '%s'
          """
              .formatted(activeFlag, escapeSqlLiteral(accountCode)));
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "Failed to update account active flag for SQLite fuzz setup.", exception);
    }
  }

  /** Builds deterministic protected-book passphrase material for one fuzz or replay command. */
  public static SqliteBookPassphrase bookPassphrase() {
    return SqliteBookPassphrase.fromCharacters(
        "jazzer deterministic book passphrase", TEST_BOOK_KEY.toCharArray());
  }

  /** Writes one deterministic secure key file that resolves to the shared Jazzer passphrase. */
  public static void writeDeterministicBookKeyFile(Path keyFilePath) throws java.io.IOException {
    if (Files.notExists(keyFilePath)) {
      SqliteBookKeyFileGenerator.generate(keyFilePath);
    } else {
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyFilePath).requireAccepted();
    }
    Files.writeString(keyFilePath, TEST_BOOK_KEY, StandardCharsets.UTF_8);
  }

  /** Creates or hardens one owner-only directory for deterministic SQLite fuzz artifacts. */
  public static void prepareSecureArtifactDirectory(Path directoryPath) throws java.io.IOException {
    Path normalizedDirectory = directoryPath.toAbsolutePath().normalize();
    if (Files.exists(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException(
            "SQLite fuzz artifact directory must be one directory path: " + normalizedDirectory);
      }
    } else {
      Files.createDirectories(normalizedDirectory);
    }
    SqliteBookFileSecurity.hardenDirectory(normalizedDirectory);
    SqliteBookKeyFileSecurity.hardenDirectory(normalizedDirectory);
  }

  /** Opens one deterministic protected-book store for fuzz and replay flows. */
  public static SqliteBookSession openStore(Path bookPath) {
    return SqliteBookSessions.open(bookPath, bookPassphrase());
  }

  /** Asserts that one open store connection keeps FinGrind's connection-hardening pragmas. */
  public static void assertStoreConnectionHardening(SqliteBookSession postingFactStore) {
    try {
      SqliteNativeDatabase database =
          requireStoreImplementation(postingFactStore).activeNativeDatabase();
      assertQueryInt(database, "pragma foreign_keys", 1);
      assertQueryText(database, "pragma journal_mode", "delete");
      assertQueryInt(database, "pragma synchronous", 3);
      assertQueryInt(database, "pragma trusted_schema", 0);
    } catch (SqliteNativeException exception) {
      throw new IllegalStateException(
          "SQLite store connection did not satisfy the pragma-hardening invariant.", exception);
    }
  }

  static void assertQueryInt(SqliteNativeDatabase database, String sql, int expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCodes.ROW) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      int actualValue = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCodes.DONE) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (actualValue != expectedValue) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static void assertQueryText(SqliteNativeDatabase database, String sql, String expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCodes.ROW) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      String actualValue = statement.columnText(0);
      if (statement.step() != SqliteNativeResultCodes.DONE) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (!expectedValue.equalsIgnoreCase(actualValue)) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static String escapeSqlLiteral(String text) {
    return text.replace("'", "''");
  }

  static SqlitePostingFactStore requireStoreImplementation(SqliteBookSession session) {
    if (session instanceof SqlitePostingFactStore store) {
      return store;
    }
    throw new IllegalArgumentException(
        "Unsupported SQLite book session implementation: " + session.getClass().getName());
  }
}
