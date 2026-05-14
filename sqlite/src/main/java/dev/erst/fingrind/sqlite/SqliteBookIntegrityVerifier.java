package dev.erst.fingrind.sqlite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Verifies that one opened SQLite book matches FinGrind's persisted money and schema contract. */
final class SqliteBookIntegrityVerifier {
  private SqliteBookIntegrityVerifier() {}

  static boolean passesIntegrityCheck(SqliteNativeDatabase activeDatabase) {
    return hasSingleOkResult(activeDatabase, SqlitePostingSql.PRAGMA_INTEGRITY_CHECK);
  }

  static boolean passesForeignKeyCheck(SqliteNativeDatabase activeDatabase) {
    return !SqliteStatementQueries.existsRow(
        activeDatabase, SqlitePostingSql.PRAGMA_FOREIGN_KEY_CHECK, statement -> {});
  }

  static void recordSchemaFingerprint(SqliteNativeDatabase activeDatabase) {
    SqliteMutationWriter.insertBookMetaValue(
        activeDatabase,
        SqlitePostingSql.SCHEMA_FINGERPRINT_META_KEY,
        liveSchemaFingerprint(activeDatabase));
  }

  static boolean hasMatchingRecordedSchemaFingerprint(SqliteNativeDatabase activeDatabase) {
    Optional<String> recordedFingerprint =
        SqliteStatementQueries.loadOptionalText(
            activeDatabase,
            SqlitePostingSql.FIND_BOOK_META_VALUE,
            statement -> statement.bindText(1, SqlitePostingSql.SCHEMA_FINGERPRINT_META_KEY));
    return recordedFingerprint.isPresent()
        && recordedFingerprint.orElseThrow().equals(liveSchemaFingerprint(activeDatabase));
  }

  static boolean hasBalancedPersistedJournal(SqliteNativeDatabase activeDatabase) {
    return !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_POSTING_WITHOUT_JOURNAL_LINES, statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_UNBALANCED_POSTING, statement -> {});
  }

  static boolean hasValidPersistedMoney(SqliteNativeDatabase activeDatabase) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.LOAD_PERSISTED_MONEY_AUDIT_ROWS)) {
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        SqlitePersistedMoneyCodec.readPositiveMoney(statement, 0, 1);
      }
      return true;
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return false;
    }
  }

  static boolean hasFunctionalCurrencyAlignedJournal(SqliteNativeDatabase activeDatabase) {
    return !SqliteStatementQueries.existsRow(
        activeDatabase,
        SqlitePostingSql.FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY,
        statement -> {});
  }

  static String liveSchemaFingerprint(SqliteNativeDatabase activeDatabase) {
    StringBuilder material = new StringBuilder(1024);
    int rowCount = 0;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.LOAD_CANONICAL_SCHEMA_OBJECTS)) {
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        material
            .append(SqlitePostingMapper.requiredText(statement, 0))
            .append('\u0000')
            .append(SqlitePostingMapper.requiredText(statement, 1))
            .append('\u0000')
            .append(SqlitePostingMapper.requiredText(statement, 2))
            .append('\n');
        rowCount++;
      }
    }
    if (rowCount != SqlitePostingSql.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT) {
      throw new IllegalStateException(
          "SQLite canonical schema fingerprint expected "
              + SqlitePostingSql.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT
              + " objects but found "
              + rowCount
              + ".");
    }
    return sha256Hex(material.toString());
  }

  private static boolean hasSingleOkResult(SqliteNativeDatabase activeDatabase, String sql) {
    SqliteStatementQueries.OptionalTextRow row =
        SqliteStatementQueries.loadOptionalTextRow(activeDatabase, sql, statement -> {});
    return row.singleRow() && "ok".equals(row.value().orElse(null));
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
    }
  }
}
