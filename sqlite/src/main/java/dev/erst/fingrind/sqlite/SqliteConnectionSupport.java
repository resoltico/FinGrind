package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.OptionalInt;

/** Shared SQLite connection open/configuration helpers. */
final class SqliteConnectionSupport {
  private static final String REQUIRED_JOURNAL_MODE = "delete";
  private static final int REQUIRED_SYNCHRONOUS_MODE = 3;

  private SqliteConnectionSupport() {}

  static SqliteNativeDatabase configureOpenedDatabase(
      SqliteNativeDatabase openedDatabase, SqlitePostingFactStore.AccessMode accessMode)
      throws SqliteNativeException {
    try {
      Objects.requireNonNull(accessMode, "accessMode");
      openedDatabase.executeScript(
          """
          pragma foreign_keys = on;
          %spragma synchronous = extra;
          pragma trusted_schema = off;
          pragma secure_delete = on;
          pragma temp_store = memory;
          pragma memory_security = fill;
          pragma query_only = %d;
          """
              .formatted(
                  accessMode.writesJournalMode() ? "pragma journal_mode = delete;\n" : "",
                  accessMode.queryOnlyPragmaValue()));
      assertOpenConfiguration(openedDatabase, accessMode);
      return openedDatabase;
    } catch (SqliteNativeException | RuntimeException exception) {
      closeAfterConfigurationFailure(openedDatabase);
      throw exception;
    }
  }

  static void closeAfterConfigurationFailure(SqliteNativeDatabase openedDatabase) {
    try {
      openedDatabase.close();
    } catch (SqliteNativeException ignored) {
      // Preserve the original open/configuration failure.
    }
  }

  static void requireOptionalPragmaValue(
      OptionalInt actualValue, int expectedValue, String failureMessage) {
    Objects.requireNonNull(actualValue, "actualValue");
    Objects.requireNonNull(failureMessage, "failureMessage");
    if (actualValue.isPresent() && actualValue.orElseThrow() != expectedValue) {
      throw new IllegalStateException(failureMessage);
    }
  }

  private static void assertOpenConfiguration(
      SqliteNativeDatabase openedDatabase, SqlitePostingFactStore.AccessMode accessMode)
      throws SqliteNativeException {
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma foreign_keys") != 1) {
      throw new IllegalStateException("SQLite connection failed to keep foreign_keys enabled.");
    }
    if (!REQUIRED_JOURNAL_MODE.equalsIgnoreCase(
        SqliteStatementQuerySupport.querySingleText(openedDatabase, "pragma journal_mode"))) {
      throw new IllegalStateException("SQLite connection failed to enforce journal_mode=DELETE.");
    }
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma synchronous")
        != REQUIRED_SYNCHRONOUS_MODE) {
      throw new IllegalStateException("SQLite connection failed to enforce synchronous=EXTRA.");
    }
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma trusted_schema") != 0) {
      throw new IllegalStateException("SQLite connection failed to disable trusted_schema.");
    }
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma secure_delete") != 1) {
      throw new IllegalStateException("SQLite connection failed to enable secure_delete.");
    }
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma temp_store") != 2) {
      throw new IllegalStateException("SQLite connection failed to force temp_store=MEMORY.");
    }
    requireOptionalPragmaValue(
        SqliteStatementQuerySupport.queryOptionalInt(openedDatabase, "pragma memory_security"),
        1,
        "SQLite connection failed to enable memory_security=fill.");
    if (SqliteStatementQuerySupport.querySingleInt(openedDatabase, "pragma query_only")
        != accessMode.queryOnlyPragmaValue()) {
      throw new IllegalStateException(
          "SQLite connection failed to enforce the expected query_only setting.");
    }
  }
}
