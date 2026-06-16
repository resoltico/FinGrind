package dev.erst.fingrind.sqlite;

import java.util.List;

/** Reads lifecycle and compatibility state from one selected SQLite database handle. */
final class SqliteBookStateReader {
  /**
   * Chooses whether one state snapshot proves only operational readiness or full semantic health.
   */
  private enum VerificationMode {
    /** Verifies the structural markers required for ordinary read-only command execution. */
    OPERATIONAL,
    /** Verifies the full semantic audit required for explicit book inspection and write paths. */
    AUDITED
  }

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
    return snapshot(activeDatabase, VerificationMode.AUDITED);
  }

  SqliteBookStateSnapshot operationalSnapshot(SqliteNativeDatabase activeDatabase) {
    return snapshot(activeDatabase, VerificationMode.OPERATIONAL);
  }

  private SqliteBookStateSnapshot snapshot(
      SqliteNativeDatabase activeDatabase, VerificationMode verificationMode) {
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
          && hasCanonicalInitializedBookStructure(activeDatabase)
          && (verificationMode == VerificationMode.OPERATIONAL
              || hasAuditedInitializedBookSemantics(activeDatabase))) {
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

  private boolean hasCanonicalInitializedBookStructure(SqliteNativeDatabase activeDatabase) {
    try {
      if (!SqliteBookIntegrityVerifier.hasNoUnexpectedSchemaObjects(activeDatabase)) {
        return false;
      }
      if (!SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(activeDatabase)) {
        return false;
      }
      boolean initializedAtPresent =
          SqliteStatementQueries.loadInitializedAt(activeDatabase).isPresent();
      return initializedAtPresent
          && SqliteStatementQueries.loadBookIdentity(activeDatabase).isPresent();
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return false;
    }
  }

  private boolean hasAuditedInitializedBookSemantics(SqliteNativeDatabase activeDatabase) {
    try {
      if (!SqliteBookIntegrityVerifier.passesIntegrityCheck(activeDatabase)) {
        return false;
      }
      if (!SqliteBookIntegrityVerifier.passesForeignKeyCheck(activeDatabase)) {
        return false;
      }
      if (!SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(activeDatabase)) {
        return false;
      }
      if (!SqliteBookIntegrityVerifier.hasValidPersistedMoney(activeDatabase)) {
        return false;
      }
      return SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(activeDatabase)
          && SqliteBookIntegrityVerifier.hasValidPersistedPostingLifecycle(activeDatabase);
    } catch (IllegalArgumentException | IllegalStateException exception) {
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
