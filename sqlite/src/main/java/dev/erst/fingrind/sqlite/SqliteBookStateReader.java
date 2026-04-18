package dev.erst.fingrind.sqlite;

/** Reads lifecycle and compatibility state from one selected SQLite database handle. */
final class SqliteBookStateReader {
  private final int bookApplicationId;
  private final int bookFormatVersion;
  private final String accountTable;
  private final String bookMetaTable;
  private final String journalLineTable;
  private final String postingFactTable;

  SqliteBookStateReader(
      int bookApplicationId,
      int bookFormatVersion,
      String accountTable,
      String bookMetaTable,
      String journalLineTable,
      String postingFactTable) {
    this.bookApplicationId = bookApplicationId;
    this.bookFormatVersion = bookFormatVersion;
    this.accountTable = accountTable;
    this.bookMetaTable = bookMetaTable;
    this.journalLineTable = journalLineTable;
    this.postingFactTable = postingFactTable;
  }

  SqliteBookState bookState(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    int applicationId =
        SqliteStatementQuerySupport.querySingleInt(activeDatabase, "pragma application_id");
    int userVersion =
        SqliteStatementQuerySupport.querySingleInt(activeDatabase, "pragma user_version");
    if (applicationId == 0 && userVersion == 0 && !hasUserSchemaObjects(activeDatabase)) {
      return SqliteBookState.BLANK_SQLITE;
    }
    if (applicationId == bookApplicationId) {
      if (userVersion != bookFormatVersion) {
        return SqliteBookState.UNSUPPORTED_FINGRIND_VERSION;
      }
      if (hasCanonicalTables(activeDatabase) && hasInitializedMarker(activeDatabase)) {
        return SqliteBookState.INITIALIZED_FINGRIND;
      }
      return SqliteBookState.INCOMPLETE_FINGRIND;
    }
    return SqliteBookState.FOREIGN_SQLITE;
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    return bookState(activeDatabase) == SqliteBookState.INITIALIZED_FINGRIND;
  }

  private boolean hasUserSchemaObjects(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return SqliteStatementQuerySupport.existsRow(
        activeDatabase, SqlitePostingSql.USER_SCHEMA_EXISTS, statement -> {});
  }

  private boolean hasCanonicalTables(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsTable(activeDatabase, bookMetaTable)
        && existsTable(activeDatabase, accountTable)
        && existsTable(activeDatabase, postingFactTable)
        && existsTable(activeDatabase, journalLineTable);
  }

  private boolean hasInitializedMarker(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsTable(activeDatabase, bookMetaTable)
        && SqliteStatementQuerySupport.existsRow(
            activeDatabase,
            SqlitePostingSql.BOOK_INITIALIZED_EXISTS,
            statement -> statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY));
  }

  private boolean existsTable(SqliteNativeDatabase activeDatabase, String tableName)
      throws SqliteNativeException {
    return SqliteStatementQuerySupport.existsRow(
        activeDatabase,
        SqlitePostingSql.TABLE_EXISTS,
        statement -> statement.bindText(1, tableName));
  }
}
