package dev.erst.fingrind.sqlite;

import java.util.List;

/** Reads lifecycle and compatibility state from one selected SQLite database handle. */
final class SqliteBookStateReader {
  private final int bookApplicationId;
  private final int bookFormatVersion;
  private final List<String> canonicalTables;

  SqliteBookStateReader(
      int bookApplicationId, int bookFormatVersion, List<String> canonicalTables) {
    this.bookApplicationId = bookApplicationId;
    this.bookFormatVersion = bookFormatVersion;
    this.canonicalTables = List.copyOf(canonicalTables);
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
    return canonicalTables.stream().allMatch(tableName -> existsTable(activeDatabase, tableName));
  }

  boolean hasInitializedMarker(SqliteNativeDatabase activeDatabase) {
    return existsTable(activeDatabase, SqliteBookContract.BOOK_META_TABLE)
        && SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.BOOK_INITIALIZED_EXISTS,
            statement -> statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY));
  }

  private boolean hasCanonicalInitializedBookSemantics(SqliteNativeDatabase activeDatabase) {
    try {
      return SqliteBookIntegrityVerifier.hasNoUnexpectedSchemaObjects(activeDatabase)
          && SqliteBookIntegrityVerifier.passesIntegrityCheck(activeDatabase)
          && SqliteBookIntegrityVerifier.passesForeignKeyCheck(activeDatabase)
          && SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(activeDatabase)
          && SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(activeDatabase)
          && SqliteStatementQueries.loadBookIdentity(activeDatabase).isPresent()
          && SqliteBookIntegrityVerifier.hasValidPersistedMoney(activeDatabase)
          && SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(activeDatabase);
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
