package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests semantic integrity gating for initialized SQLite books. */
class SqliteBookStateReaderTest extends SqlitePostingFactStoreTestSupport {
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
        "book-state-invalid-period-result-transfer-target.sqlite",
        database -> {
          database.executeStatement(
              "drop trigger period_result_transfer_validate_closing_equity_account_on_insert");
          database.executeStatement(
              """
              insert into period_result_transfer (
                  period_result_transfer_order,
                  effective_date_from,
                  effective_date_to,
                  closing_equity_account_code,
                  closed_at
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
