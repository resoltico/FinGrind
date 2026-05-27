package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Shared SQLite connection open/configuration helpers. */
final class SqliteConnectionConfigurer {
  private static final String REQUIRED_JOURNAL_MODE = "delete";
  private static final int REQUIRED_SYNCHRONOUS_MODE = 3;

  private SqliteConnectionConfigurer() {}

  static SqliteNativeDatabase configureOpenedDatabase(
      SqliteNativeDatabase openedDatabase, SqliteStoreAccessMode accessMode) {
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
    } catch (RuntimeException exception) {
      closeAfterConfigurationFailure(openedDatabase);
      throw exception;
    }
  }

  static void closeAfterConfigurationFailure(SqliteNativeDatabase openedDatabase) {
    closeAfterConfigurationFailure(openedDatabase, SqliteBestEffort::reportCleanupFailure);
  }

  static void closeAfterConfigurationFailure(
      SqliteNativeDatabase openedDatabase, SqliteBestEffort.Reporter reporter) {
    try {
      openedDatabase.close();
    } catch (SqliteNativeException exception) {
      reporter.report("closing one SQLite database after configuration failure", exception);
    }
  }

  static void requirePragmaValue(int actualValue, int expectedValue, String failureMessage) {
    Objects.requireNonNull(failureMessage, "failureMessage");
    if (actualValue != expectedValue) {
      throw new IllegalStateException(failureMessage);
    }
  }

  static void assertOpenConfiguration(
      SqliteNativeDatabase openedDatabase, SqliteStoreAccessMode accessMode) {
    requireForeignKeysEnabled(openedDatabase);
    requireJournalMode(openedDatabase);
    requireSynchronousExtra(openedDatabase);
    requireTrustedSchemaDisabled(openedDatabase);
    requireSecureDeleteEnabled(openedDatabase);
    requireTempStoreMemory(openedDatabase);
    requireMemorySecurityFill(openedDatabase);
    requireExpectedQueryOnly(openedDatabase, accessMode);
  }

  private static void requireForeignKeysEnabled(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma foreign_keys"),
        1,
        "SQLite connection failed to keep foreign_keys enabled.");
  }

  private static void requireJournalMode(SqliteNativeDatabase openedDatabase) {
    if (!REQUIRED_JOURNAL_MODE.equalsIgnoreCase(
        SqliteStatementQueries.querySingleText(openedDatabase, "pragma journal_mode"))) {
      throw new IllegalStateException("SQLite connection failed to enforce journal_mode=DELETE.");
    }
  }

  private static void requireSynchronousExtra(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma synchronous"),
        REQUIRED_SYNCHRONOUS_MODE,
        "SQLite connection failed to enforce synchronous=EXTRA.");
  }

  private static void requireTrustedSchemaDisabled(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma trusted_schema"),
        0,
        "SQLite connection failed to disable trusted_schema.");
  }

  private static void requireSecureDeleteEnabled(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma secure_delete"),
        1,
        "SQLite connection failed to enable secure_delete.");
  }

  private static void requireTempStoreMemory(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma temp_store"),
        2,
        "SQLite connection failed to force temp_store=MEMORY.");
  }

  private static void requireMemorySecurityFill(SqliteNativeDatabase openedDatabase) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma memory_security"),
        1,
        "SQLite connection failed to enable memory_security=fill.");
  }

  private static void requireExpectedQueryOnly(
      SqliteNativeDatabase openedDatabase, SqliteStoreAccessMode accessMode) {
    requirePragmaValue(
        SqliteStatementQueries.querySingleInt(openedDatabase, "pragma query_only"),
        accessMode.queryOnlyPragmaValue(),
        "SQLite connection failed to enforce the expected query_only setting.");
  }
}
