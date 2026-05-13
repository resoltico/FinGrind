package dev.erst.fingrind.sqlite;

/** Reads lifecycle and compatibility state from one selected SQLite database handle. */
final class SqliteBookStateReader {
  private final int bookApplicationId;
  private final int bookFormatVersion;
  private final String accountTable;
  private final String auditEventTable;
  private final String bookMetaTable;
  private final String journalLineTable;
  private final String postingFactTable;

  SqliteBookStateReader(
      int bookApplicationId,
      int bookFormatVersion,
      String accountTable,
      String auditEventTable,
      String bookMetaTable,
      String journalLineTable,
      String postingFactTable) {
    this.bookApplicationId = bookApplicationId;
    this.bookFormatVersion = bookFormatVersion;
    this.accountTable = accountTable;
    this.auditEventTable = auditEventTable;
    this.bookMetaTable = bookMetaTable;
    this.journalLineTable = journalLineTable;
    this.postingFactTable = postingFactTable;
  }

  SqliteBookState bookState(SqliteNativeDatabase activeDatabase) {
    return snapshot(activeDatabase).state();
  }

  SqliteBookStateSnapshot snapshot(SqliteNativeDatabase activeDatabase) {
    int applicationId =
        SqliteStatementQueries.querySingleInt(activeDatabase, "pragma application_id");
    int userVersion = SqliteStatementQueries.querySingleInt(activeDatabase, "pragma user_version");
    if (applicationId == 0 && userVersion == 0 && !hasUserSchemaObjects(activeDatabase)) {
      return new SqliteBookStateSnapshot(applicationId, userVersion, SqliteBookState.BLANK_SQLITE);
    }
    if (applicationId == bookApplicationId) {
      if (userVersion != bookFormatVersion) {
        return new SqliteBookStateSnapshot(
            applicationId, userVersion, SqliteBookState.UNSUPPORTED_FINGRIND_VERSION);
      }
      if (hasCanonicalTables(activeDatabase)
          && hasInitializedMarker(activeDatabase)
          && hasCanonicalInitializedBookSemantics(activeDatabase)) {
        return new SqliteBookStateSnapshot(
            applicationId, userVersion, SqliteBookState.INITIALIZED_FINGRIND);
      }
      return new SqliteBookStateSnapshot(
          applicationId, userVersion, SqliteBookState.INCOMPLETE_FINGRIND);
    }
    return new SqliteBookStateSnapshot(applicationId, userVersion, SqliteBookState.FOREIGN_SQLITE);
  }

  private boolean hasUserSchemaObjects(SqliteNativeDatabase activeDatabase) {
    return SqliteStatementQueries.existsRow(
        activeDatabase, SqlitePostingSql.USER_SCHEMA_EXISTS, statement -> {});
  }

  boolean hasCanonicalTables(SqliteNativeDatabase activeDatabase) {
    return existsTable(activeDatabase, bookMetaTable)
        && existsTable(activeDatabase, accountTable)
        && existsTable(activeDatabase, auditEventTable)
        && existsTable(activeDatabase, postingFactTable)
        && existsTable(activeDatabase, journalLineTable);
  }

  boolean hasInitializedMarker(SqliteNativeDatabase activeDatabase) {
    return existsTable(activeDatabase, bookMetaTable)
        && SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.BOOK_INITIALIZED_EXISTS,
            statement -> statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY));
  }

  private boolean hasCanonicalInitializedBookSemantics(SqliteNativeDatabase activeDatabase) {
    try {
      return SqliteBookIntegrityVerifier.passesIntegrityCheck(activeDatabase)
          && SqliteBookIntegrityVerifier.passesForeignKeyCheck(activeDatabase)
          && SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(activeDatabase)
          && SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(activeDatabase)
          && SqliteBookIntegrityVerifier.hasValidPersistedMoney(activeDatabase);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean existsTable(SqliteNativeDatabase activeDatabase, String tableName) {
    return SqliteStatementQueries.existsRow(
        activeDatabase,
        SqlitePostingSql.TABLE_EXISTS,
        statement -> statement.bindText(1, tableName));
  }
}
