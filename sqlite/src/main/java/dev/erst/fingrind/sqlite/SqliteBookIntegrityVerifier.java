package dev.erst.fingrind.sqlite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
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

  static boolean hasNoUnexpectedSchemaObjects(SqliteNativeDatabase activeDatabase) {
    return !SqliteStatementQueries.existsRow(
        activeDatabase, SqlitePostingSql.LOAD_NON_CANONICAL_SCHEMA_OBJECTS, statement -> {});
  }

  static boolean hasValidPersistedMoney(SqliteNativeDatabase activeDatabase) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.LOAD_PERSISTED_MONEY_AUDIT_ROWS)) {
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
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

  static boolean hasValidPersistedPostingLifecycle(SqliteNativeDatabase activeDatabase) {
    return !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_LATE_OPENING_BALANCE_POSTING, statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_OPENING_BALANCE_NOMINAL_ACCOUNT, statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT, statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD,
            statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING,
            statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK,
            statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT,
            statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.FIND_INVALID_FISCAL_YEAR_CLOSE_LINK, statement -> {})
        && !SqliteStatementQueries.existsRow(
            activeDatabase,
            SqlitePostingSql.FIND_INVALID_FISCAL_YEAR_CLOSE_TARGET_ACCOUNT,
            statement -> {});
  }

  static boolean hasConsistentInventoryOnHand(SqliteNativeDatabase activeDatabase) {
    Map<String, InventoryReplayState> replayedStates = SqliteReportRowValues.insertionOrderedMap();
    Map<String, Long> nextExpectedSequences = SqliteReportRowValues.insertionOrderedMap();
    boolean validReplay =
        SqliteStatementQueries.queryWithStatement(
            activeDatabase,
            SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENT_REPLAY_ROWS,
            statement -> {
              while (statement.step() == SqliteNativeResultCode.code("ROW")) {
                String inventoryAccount = SqlitePostingMapper.requiredText(statement, 0);
                long accountSequence = statement.columnLong(2);
                long expectedSequence = nextExpectedSequences.getOrDefault(inventoryAccount, 1L);
                if (accountSequence != expectedSequence) {
                  return false;
                }
                LocalDate effectiveDate =
                    dev.erst.fingrind.core.CanonicalTemporalText.parseLocalDate(
                        SqlitePostingMapper.requiredText(statement, 1),
                        "inventoryMovement.effectiveDate");
                InventoryReplayState nextState =
                    replayedStates
                        .getOrDefault(inventoryAccount, InventoryReplayState.ZERO)
                        .apply(statement.columnLong(3), statement.columnLong(4), effectiveDate);
                if (!nextState.valid()) {
                  return false;
                }
                replayedStates.put(inventoryAccount, nextState);
                nextExpectedSequences.put(inventoryAccount, Math.incrementExact(accountSequence));
              }
              return true;
            });
    if (!validReplay) {
      return false;
    }
    Map<String, InventoryReplayState> persistedStates = SqliteReportRowValues.insertionOrderedMap();
    boolean validPersisted =
        SqliteStatementQueries.queryWithStatement(
            activeDatabase,
            SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_ROWS,
            statement -> {
              while (statement.step() == SqliteNativeResultCode.code("ROW")) {
                InventoryReplayState persistedState =
                    InventoryReplayState.persisted(
                        statement.columnLong(1),
                        statement.columnLong(2),
                        dev.erst.fingrind.core.CanonicalTemporalText.parseLocalDate(
                            SqlitePostingMapper.requiredText(statement, 3),
                            "inventoryOnHand.lastMovementDate"));
                if (!persistedState.valid()) {
                  return false;
                }
                persistedStates.put(SqlitePostingMapper.requiredText(statement, 0), persistedState);
              }
              return Boolean.TRUE;
            });
    return validPersisted && replayedStates.equals(persistedStates);
  }

  static String liveSchemaFingerprint(SqliteNativeDatabase activeDatabase) {
    StringBuilder material = new StringBuilder(1024);
    int rowCount = 0;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.LOAD_CANONICAL_SCHEMA_OBJECTS)) {
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
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
    SqliteOptionalTextRow row =
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

  private record InventoryReplayState(
      long quantity, long costPoolMinor, Optional<LocalDate> lastMovementDate) {
    private static final InventoryReplayState ZERO =
        new InventoryReplayState(0L, 0L, Optional.empty());

    private static InventoryReplayState persisted(
        long quantity, long costPoolMinor, LocalDate lastMovementDate) {
      return new InventoryReplayState(quantity, costPoolMinor, Optional.of(lastMovementDate));
    }

    private InventoryReplayState apply(
        long quantityDelta, long costDeltaMinor, LocalDate effectiveDate) {
      return new InventoryReplayState(
          Math.addExact(quantity, quantityDelta),
          Math.addExact(costPoolMinor, costDeltaMinor),
          Optional.of(effectiveDate));
    }

    private boolean valid() {
      return quantity >= 0L
          && costPoolMinor >= 0L
          && ((quantity == 0L) == (costPoolMinor == 0L))
          && lastMovementDate.isPresent();
    }
  }
}
