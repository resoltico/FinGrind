package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests semantic integrity gating for initialized SQLite books. */
class SqliteBookStateReaderTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void stateReader_distinguishesBlankForeignUnsupportedAndIncompleteBookHeaders() {
    withStandaloneDatabase(
        bookAccess(tempDirectory.resolve("book-state-blank.sqlite")),
        database ->
            assertEquals(
                SqliteBookState.BLANK_SQLITE,
                SqliteBookContract.BOOK_STATE_READER.bookState(database)));

    withStandaloneDatabase(
        bookAccess(tempDirectory.resolve("book-state-foreign.sqlite")),
        database -> {
          database.executeStatement("pragma application_id = 7");
          database.executeStatement("pragma user_version = 1");
          assertEquals(
              SqliteBookState.FOREIGN_SQLITE,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });

    withStandaloneDatabase(
        bookAccess(tempDirectory.resolve("book-state-unsupported.sqlite")),
        database -> {
          database.executeStatement("pragma application_id = " + SqliteBookContract.APPLICATION_ID);
          database.executeStatement(
              "pragma user_version = " + (SqliteBookContract.FORMAT_VERSION + 1));
          assertEquals(
              SqliteBookState.UNSUPPORTED_FINGRIND_VERSION,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });

    withStandaloneDatabase(
        bookAccess(tempDirectory.resolve("book-state-incomplete.sqlite")),
        database -> {
          database.executeStatement("pragma application_id = " + SqliteBookContract.APPLICATION_ID);
          database.executeStatement("pragma user_version = " + SqliteBookContract.FORMAT_VERSION);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
  }

  @Test
  void initializedMarker_requiresBothTheCanonicalTableAndTheInitializedTimestamp() {
    Path bookPath = tempDirectory.resolve("book-state-initialized-marker.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertFalse(SqliteBookContract.BOOK_STATE_READER.hasInitializedMarker(database));
          SqliteBookSchemaBootstrap.initializeBook(database);
          assertFalse(SqliteBookContract.BOOK_STATE_READER.hasInitializedMarker(database));
          insertInitializedAtRow(database);
          assertTrue(SqliteBookContract.BOOK_STATE_READER.hasInitializedMarker(database));
        });
  }

  @Test
  void initializedBookState_recognizesHealthyInitializedBooks() {
    Path initializedBookPath = tempDirectory.resolve("book-state-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database ->
            assertEquals(
                SqliteBookState.INITIALIZED_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(database)));
  }

  @Test
  void operationalSnapshot_acceptsHealthyInitializedBooksWithoutDeepAuditProbes() {
    Path initializedBookPath = tempDirectory.resolve("book-state-operational.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          SqliteNativeDatabase operationalDatabase =
              InterceptingSqliteNativeDatabase.throwing(
                  InterceptingSqliteNativeDatabase.throwing(
                      database,
                      SqlitePostingSql.PRAGMA_FOREIGN_KEY_CHECK,
                      new SqliteNativeException(
                          SqliteNativeResultCode.code("IOERR_LOCK"),
                          "forced foreign-key-check lock failure")),
                  SqlitePostingSql.PRAGMA_INTEGRITY_CHECK,
                  new SqliteNativeException(
                      SqliteNativeResultCode.code("IOERR_LOCK"),
                      "forced integrity-check lock failure"));
          assertEquals(
              SqliteBookState.INITIALIZED_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER
                  .operationalSnapshot(operationalDatabase)
                  .state());
        });
  }

  @Test
  void operationalSnapshot_requiresCanonicalFingerprintInitializationMarkerAndBookIdentity() {
    Path fingerprintPath = tempDirectory.resolve("book-state-operational-fingerprint.sqlite");
    String mismatchedSchemaFingerprint = "0".repeat(64);
    initializeBookOnDisk(fingerprintPath);
    withStandaloneDatabase(
        bookAccess(fingerprintPath),
        database -> {
          database.executeStatement(
              """
              update book_meta
              set value = '%s'
              where meta_key = 'schema_fingerprint_sha256'
              """
                  .formatted(mismatchedSchemaFingerprint));
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.operationalSnapshot(database).state());
        });

    Path inconsistentInitializedAtPath =
        tempDirectory.resolve("book-state-operational-inconsistent-initialized-at.sqlite");
    initializeBookOnDisk(inconsistentInitializedAtPath);
    withStandaloneDatabase(
        bookAccess(inconsistentInitializedAtPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER
                    .operationalSnapshot(
                        InterceptingSqliteNativeDatabase.replacing(
                            database,
                            SqlitePostingSql.FIND_BOOK_INITIALIZED_AT,
                            "select value from book_meta where meta_key = ? and 1 = 0"))
                    .state()));

    Path missingInitializedAtPath =
        tempDirectory.resolve("book-state-operational-missing-initialized-at.sqlite");
    createSchemaOnlyBook(missingInitializedAtPath);
    withStandaloneDatabase(
        bookAccess(missingInitializedAtPath),
        database -> {
          SqliteBookIntegrityVerifier.recordSchemaFingerprint(database);
          SqliteMutationWriter.insertBookIdentity(database, bookIdentity());
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.operationalSnapshot(database).state());
        });

    Path missingBookIdentityPath =
        tempDirectory.resolve("book-state-operational-missing-book-identity.sqlite");
    createSchemaOnlyBook(missingBookIdentityPath);
    withStandaloneDatabase(
        bookAccess(missingBookIdentityPath),
        database -> {
          insertInitializedAtRow(database);
          SqliteBookIntegrityVerifier.recordSchemaFingerprint(database);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.operationalSnapshot(database).state());
        });
  }

  @Test
  void operationalSnapshot_rejectsMismatchedPersistedInventoryCostingDoctrine() {
    Path bookPath = tempDirectory.resolve("book-state-operational-costing-mismatch.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER
                    .operationalSnapshot(
                        InterceptingSqliteNativeDatabase.replacing(
                            database,
                            SqlitePostingSql.FIND_BOOK_IDENTITY_CORE,
                            """
                            select
                                entity_name,
                                accounting_kernel_profile,
                                accounting_basis,
                                accounting_framework_position,
                                entity_form,
                                book_template_id,
                                'WEIGHTED_AVERAGE' as costing_doctrine,
                                functional_currency_code,
                                fiscal_year_start_month,
                                fiscal_year_start_day,
                                book_start_effective_date
                            from book_identity
                            where singleton_id = 1
                            limit 1
                            """))
                    .state()));
  }

  @Test
  void initializedBookState_rejectsHealthyBookWhenPersistedMoneyAuditFails() {
    Path initializedBookPath = tempDirectory.resolve("book-state-invalid-persisted-money.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.replacing(
                        database,
                        SqlitePostingSql.LOAD_PERSISTED_MONEY_AUDIT_ROWS,
                        "select 'ZZZ' as currency_code, -1 as amount_minor"))));
  }

  @Test
  void initializedBookState_rejectsHealthyBookWhenFunctionalCurrencyProbeFindsMismatch() {
    Path initializedBookPath =
        tempDirectory.resolve("book-state-functional-currency-probe-mismatch.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.replacing(
                        database,
                        SqlitePostingSql.FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY,
                        "select 'posting-1'"))));
  }

  @Test
  void initializedBookState_rejectsInventoryConsistencyProbeFailures() {
    Path initializedBookPath = tempDirectory.resolve("book-state-inventory-probe-failure.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.throwing(
                        database,
                        SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENT_REPLAY_ROWS,
                        new IllegalStateException("forced inventory consistency probe failure")))));
  }

  @Test
  void initializedBookState_rejectsInventoryConsistencyMismatches() {
    Path initializedBookPath = tempDirectory.resolve("book-state-inventory-probe-mismatch.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.replacing(
                        database,
                        SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_ROWS,
                        "select '1400' as inventory_account, 1 as quantity, 100 as cost_pool_minor, '2026-04-07' as last_movement_date"))));
  }

  @Test
  void initializedBookState_requiresIntegrityForeignKeyFingerprintBalanceAndMoneyIntegrity() {
    Path fingerprintPath = tempDirectory.resolve("book-state-fingerprint.sqlite");
    String mismatchedSchemaFingerprint = "0".repeat(64);
    initializeBookOnDisk(fingerprintPath);
    withStandaloneDatabase(
        bookAccess(fingerprintPath),
        database -> {
          database.executeStatement(
              """
              update book_meta
              set value = '%s'
              where meta_key = 'schema_fingerprint_sha256'
              """
                  .formatted(mismatchedSchemaFingerprint));
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
    Path unexpectedSchemaPath = tempDirectory.resolve("book-state-unexpected-schema.sqlite");
    initializeBookOnDisk(unexpectedSchemaPath);
    withStandaloneDatabase(
        bookAccess(unexpectedSchemaPath),
        database -> {
          database.executeStatement("create table unexpected_table(singleton_id integer)");
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });

    Path unbalancedPath = tempDirectory.resolve("book-state-unbalanced.sqlite");
    initializeBookOnDisk(unbalancedPath);
    withStandaloneDatabase(
        bookAccess(unbalancedPath),
        database -> {
          insertPostingFactRow(database, "posting-unbalanced", "idem-unbalanced");
          insertJournalLineRow(database, "posting-unbalanced", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-unbalanced", 1, "2000", "CREDIT", "EUR", 900);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
    assertIncompleteStateAfterCorruption(
        "book-state-only-debit.sqlite",
        database -> {
          insertPostingFactRow(database, "posting-only-debit", "idem-only-debit");
          insertJournalLineRow(database, "posting-only-debit", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-only-debit", 1, "2000", "DEBIT", "EUR", 1000);
        });
    assertIncompleteStateAfterCorruption(
        "book-state-only-credit.sqlite",
        database -> {
          insertPostingFactRow(database, "posting-only-credit", "idem-only-credit");
          insertJournalLineRow(database, "posting-only-credit", 0, "1000", "CREDIT", "EUR", 1000);
          insertJournalLineRow(database, "posting-only-credit", 1, "2000", "CREDIT", "EUR", 1000);
        });
    assertIncompleteStateAfterCorruption(
        "book-state-mixed-currency.sqlite",
        "drop trigger journal_line_validate_functional_currency_on_insert",
        database -> {
          insertPostingFactRow(database, "posting-mixed-currency", "idem-mixed-currency");
          insertJournalLineRow(database, "posting-mixed-currency", 0, "1000", "DEBIT", "EUR", 1000);
          insertJournalLineRow(
              database, "posting-mixed-currency", 1, "2000", "CREDIT", "USD", 1000);
        });
    Path missingBookIdentityPath = tempDirectory.resolve("book-state-missing-book-identity.sqlite");
    createSchemaOnlyBook(missingBookIdentityPath);
    withStandaloneDatabase(
        bookAccess(missingBookIdentityPath),
        database -> {
          insertInitializedAtRow(database);
          SqliteBookIntegrityVerifier.recordSchemaFingerprint(database);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
    assertIncompleteStateAfterCorruption(
        "book-state-functional-currency-mismatch.sqlite",
        "drop trigger journal_line_validate_functional_currency_on_insert",
        database -> {
          insertPostingFactRow(database, "posting-usd", "idem-usd");
          insertJournalLineRow(database, "posting-usd", 0, "1000", "DEBIT", "USD", 1000);
          insertJournalLineRow(database, "posting-usd", 1, "2000", "CREDIT", "USD", 1000);
        });
    assertIncompleteStateAfterCorruption(
        "book-state-invalid-interim-result-sweep-target.sqlite",
        database -> {
          database.executeStatement(
              "drop trigger interim_result_sweep_validate_result_holding_account_on_insert");
          database.executeStatement(
              """
              insert into interim_result_sweep (
                  interim_result_sweep_order,
                  effective_date_from,
                  effective_date_to,
                  result_holding_account_code,
                  swept_at
              ) values (
                  1,
                  '2026-04-01',
                  '2026-04-30',
                  '1000',
                  '2026-04-30T23:59:59Z'
              )
              """);
        });

    Path invalidMoneyPath = tempDirectory.resolve("book-state-invalid-money.sqlite");
    initializeBookOnDisk(invalidMoneyPath);
    withStandaloneDatabase(
        bookAccess(invalidMoneyPath),
        database -> {
          database.executeStatement(
              "drop trigger journal_line_validate_functional_currency_on_insert");
          insertPostingFactRow(database, "posting-invalid-currency", "idem-invalid-currency");
          insertJournalLineRow(
              database, "posting-invalid-currency", 0, "1000", "DEBIT", "ZZZ", 1000);
          insertJournalLineRow(
              database, "posting-invalid-currency", 1, "2000", "CREDIT", "ZZZ", 1000);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });

    Path foreignKeyPath = tempDirectory.resolve("book-state-foreign-key.sqlite");
    initializeBookOnDisk(foreignKeyPath);
    withStandaloneDatabase(
        bookAccess(foreignKeyPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND, withForeignKeyViolationState(database)));

    Path integrityCheckPath = tempDirectory.resolve("book-state-integrity-check.sqlite");
    initializeBookOnDisk(integrityCheckPath);
    withStandaloneDatabase(
        bookAccess(integrityCheckPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.replacing(
                        database, SqlitePostingSql.PRAGMA_INTEGRITY_CHECK, "select 'corrupt'"))));

    Path throwingPath = tempDirectory.resolve("book-state-throwing.sqlite");
    initializeBookOnDisk(throwingPath);
    withStandaloneDatabase(
        bookAccess(throwingPath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.throwing(
                        database,
                        SqlitePostingSql.PRAGMA_INTEGRITY_CHECK,
                        new IllegalStateException("forced integrity-check failure")))));

    Path nativeFailurePath = tempDirectory.resolve("book-state-native-failure.sqlite");
    initializeBookOnDisk(nativeFailurePath);
    withStandaloneDatabase(
        bookAccess(nativeFailurePath),
        database -> {
          SqliteNativeException failure =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      SqliteBookContract.BOOK_STATE_READER.bookState(
                          InterceptingSqliteNativeDatabase.throwing(
                              database,
                              SqlitePostingSql.PRAGMA_INTEGRITY_CHECK,
                              new SqliteNativeException(
                                  SqliteNativeResultCode.code("IOERR_LOCK"),
                                  "forced transient lock failure"))));
          assertEquals(SqliteNativeResultCode.code("IOERR_LOCK"), failure.resultCode());
        });

    Path postingLifecycleProbePath =
        tempDirectory.resolve("book-state-posting-lifecycle-probe.sqlite");
    initializeBookOnDisk(postingLifecycleProbePath);
    withStandaloneDatabase(
        bookAccess(postingLifecycleProbePath),
        database ->
            assertEquals(
                SqliteBookState.INCOMPLETE_FINGRIND,
                SqliteBookContract.BOOK_STATE_READER.bookState(
                    InterceptingSqliteNativeDatabase.replacing(
                        database,
                        SqlitePostingSql.FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT,
                        "select 1"))));
  }

  private static SqliteBookState withForeignKeyViolationState(SqliteNativeDatabase database) {
    database.executeStatement("pragma foreign_keys = off");
    insertJournalLineRow(database, "missing-posting", 0, "1000", "DEBIT", "EUR", 1000);
    database.executeStatement("pragma foreign_keys = on");
    return SqliteBookContract.BOOK_STATE_READER.bookState(database);
  }

  private void assertIncompleteStateAfterCorruption(
      String filename, SqliteDatabaseAction corruptionAction) {
    assertIncompleteStateAfterCorruption(filename, null, corruptionAction);
  }

  private void assertIncompleteStateAfterCorruption(
      String filename, @Nullable String preCorruptionSql, SqliteDatabaseAction corruptionAction) {
    Path bookPath = tempDirectory.resolve(filename);
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          if (preCorruptionSql != null) {
            database.executeStatement(preCorruptionSql);
          }
          corruptionAction.run(database);
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
  }

  /** Test-only wrapper that rewrites or fails one targeted SQL probe while delegating the rest. */
  private static final class InterceptingSqliteNativeDatabase extends SqliteNativeDatabase {
    private final SqliteNativeDatabase delegate;
    private final String interceptedSql;
    private final @Nullable String replacementSql;
    private final @Nullable RuntimeException failure;

    private InterceptingSqliteNativeDatabase(
        SqliteNativeDatabase delegate,
        String interceptedSql,
        @Nullable String replacementSql,
        @Nullable RuntimeException failure) {
      super(MemorySegment.NULL);
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.interceptedSql = Objects.requireNonNull(interceptedSql, "interceptedSql");
      this.replacementSql = replacementSql;
      this.failure = failure;
    }

    static InterceptingSqliteNativeDatabase replacing(
        SqliteNativeDatabase delegate, String interceptedSql, String replacementSql) {
      return new InterceptingSqliteNativeDatabase(delegate, interceptedSql, replacementSql, null);
    }

    static InterceptingSqliteNativeDatabase throwing(
        SqliteNativeDatabase delegate, String interceptedSql, RuntimeException failure) {
      return new InterceptingSqliteNativeDatabase(delegate, interceptedSql, null, failure);
    }

    @Override
    SqliteNativeStatement prepare(String sql) {
      if (sql.equals(interceptedSql)) {
        if (failure != null) {
          throw failure;
        }
        return delegate.prepare(Objects.requireNonNull(replacementSql, "replacementSql"));
      }
      return delegate.prepare(sql);
    }

    @Override
    void executeStatement(String sql) {
      delegate.executeStatement(sql);
    }

    @Override
    void executeScript(String sql) {
      delegate.executeScript(sql);
    }

    @Override
    public void close() {}
  }
}
