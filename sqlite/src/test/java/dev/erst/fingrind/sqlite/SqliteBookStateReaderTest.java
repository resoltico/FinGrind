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
  void initializedBookState_requiresIntegrityForeignKeyFingerprintBalanceAndMoneyIntegrity() {
    Path fingerprintPath = tempDirectory.resolve("book-state-fingerprint.sqlite");
    initializeBookOnDisk(fingerprintPath);
    withStandaloneDatabase(
        bookAccess(fingerprintPath),
        database -> {
          database.executeStatement(
              """
              update book_meta
              set value = 'bogus'
              where key = 'schema_fingerprint_sha256'
              """);
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

    Path invalidMoneyPath = tempDirectory.resolve("book-state-invalid-money.sqlite");
    initializeBookOnDisk(invalidMoneyPath);
    withStandaloneDatabase(
        bookAccess(invalidMoneyPath),
        database -> {
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
  }

  private static SqliteBookState withForeignKeyViolationState(SqliteNativeDatabase database) {
    database.executeStatement("pragma foreign_keys = off");
    insertJournalLineRow(database, "missing-posting", 0, "1000", "DEBIT", "EUR", 1000);
    database.executeStatement("pragma foreign_keys = on");
    return SqliteBookContract.BOOK_STATE_READER.bookState(database);
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
