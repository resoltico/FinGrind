package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.BookAccess;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;

/** Shared SQLite store/bootstrap fixtures and native-handle doubles for split store tests. */
@NullUnmarked
class SqliteStoreFixtureSupport {
  static final String TEST_BOOK_KEY = "posting-fact-store-test-book-key";

  protected SqliteStoreFixtureSupport() {}

  protected final InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }

  protected final int queryInt(SqliteNativeDatabase database, String sql) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      assertEquals(SqliteNativeResultCodes.ROW, statement.step());
      int value = statement.columnInt(0);
      assertEquals(SqliteNativeResultCodes.DONE, statement.step());
      return value;
    }
  }

  protected final String queryText(SqliteNativeDatabase database, String sql) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      assertEquals(SqliteNativeResultCodes.ROW, statement.step());
      String value = statement.columnText(0);
      assertEquals(SqliteNativeResultCodes.DONE, statement.step());
      return value;
    }
  }

  static void insertInitializedAtRow(SqliteNativeDatabase database) {
    database.executeStatement(
        """
        insert into book_meta (key, value)
        values ('initialized_at', '2026-04-07T10:15:30Z')
        """);
  }

  static void insertAccountRow(
      SqliteNativeDatabase database,
      String accountCode,
      String accountName,
      String normalBalance,
      int active,
      String declaredAt) {
    database.executeStatement(
        """
        insert into account (
            account_code,
            account_name,
            normal_balance,
            active,
            declared_at
        ) values (
            '%s',
            '%s',
            '%s',
            %d,
            '%s'
        )
        """
            .formatted(accountCode, accountName, normalBalance, active, declaredAt));
  }

  static SqliteNativeDatabase staleDatabaseHandle(Path bookPath) throws IOException {
    if (bookPath.getParent() != null) {
      Files.createDirectories(bookPath.getParent());
    }
    if (Files.notExists(bookPath)) {
      Files.write(bookPath, new byte[0]);
    }
    return new ThrowingSqliteNativeDatabase();
  }

  /** Same-package deterministic native-failure double that never touches freed SQLite memory. */
  static final class ClosingSqliteNativeDatabase extends SqliteNativeDatabase {
    boolean closeAttempted;

    ClosingSqliteNativeDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    public void close() {
      closeAttempted = true;
    }

    boolean closeAttempted() {
      return closeAttempted;
    }
  }

  /** Same-package deterministic native-failure double that never touches freed SQLite memory. */
  static final class ThrowingSqliteNativeDatabase extends SqliteNativeDatabase {
    boolean closeAttempted;
    boolean closeFailed;

    ThrowingSqliteNativeDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    SqliteNativeStatement prepare(String sql) {
      throw simulatedNativeFailure("prepare a SQLite statement");
    }

    @Override
    void executeStatement(String sql) {
      throw simulatedNativeFailure("execute a SQLite statement");
    }

    @Override
    void executeScript(String sql) {
      throw simulatedNativeFailure("execute a SQLite script");
    }

    @Override
    public void close() {
      closeAttempted = true;
      if (!closeFailed) {
        closeFailed = true;
        throw simulatedNativeFailure("close a SQLite database");
      }
    }

    boolean closeAttempted() {
      return closeAttempted;
    }

    static SqliteNativeException simulatedNativeFailure(String operation) {
      return new SqliteNativeException(
          14, "Simulated SQLite native failure while attempting to " + operation + ".");
    }
  }

  static void createPostingFactOnlyBook(Path bookPath) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement(
              """
              create table posting_fact (
                  posting_id text primary key,
                  effective_date text not null,
                  recorded_at text not null,
                  actor_id text not null,
                  actor_type text not null,
                  command_id text not null,
                  idempotency_key text not null unique,
                  causation_id text not null,
                  correlation_id text null,
                  reason text null,
                  source_channel text not null,
                  prior_posting_id text null
              )
              """);
          database.executeStatement(
              """
              insert into posting_fact (
                  posting_id,
                  effective_date,
                  recorded_at,
                  actor_id,
                  actor_type,
                  command_id,
                  idempotency_key,
                  causation_id,
                  correlation_id,
                  reason,
                  source_channel,
                  prior_posting_id
              ) values (
                  'posting-partial',
                  '2026-04-07',
                  '2026-04-07T10:15:30Z',
                  'actor-1',
                  'AGENT',
                  'command-partial',
                  'idem-partial',
                  'cause-1',
                  null,
                  null,
                  'CLI',
                  null
              )
              """);
        });
  }

  static void createEmptySqliteFile(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), database -> {});
  }

  static void createSchemaOnlyBook(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), SqliteBookSchemaBootstrap::initializeBook);
  }

  static void createPartialFinGrindBook(
      Path bookPath,
      boolean includeBookMeta,
      boolean includeAccount,
      boolean includePostingFact,
      boolean includeJournalLine,
      boolean includeInitializedMarker) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement("pragma application_id = " + SqliteBookContract.APPLICATION_ID);
          database.executeStatement("pragma user_version = " + SqliteBookContract.FORMAT_VERSION);
          if (includeBookMeta) {
            database.executeStatement(
                "create table book_meta (key text primary key, value text not null)");
          }
          if (includeAccount) {
            database.executeStatement(
                """
                create table account (
                    account_code text primary key,
                    account_name text not null,
                    normal_balance text not null,
                    active integer not null,
                    declared_at text not null
                )
                """);
          }
          if (includePostingFact) {
            database.executeStatement(
                """
                create table posting_fact (
                    posting_id text primary key,
                    effective_date text not null,
                    recorded_at text not null,
                    actor_id text not null,
                    actor_type text not null,
                    command_id text not null,
                    idempotency_key text not null unique,
                    causation_id text not null,
                    correlation_id text null,
                    reason text null,
                    source_channel text not null,
                    prior_posting_id text null
                )
                """);
          }
          if (includeJournalLine) {
            database.executeStatement(
                """
                create table journal_line (
                    posting_id text not null,
                    line_order integer not null,
                    account_code text not null,
                    entry_side text not null,
                    currency_code text not null,
                    amount text not null
                )
                """);
          }
          if (includeInitializedMarker) {
            insertInitializedAtRow(database);
          }
        });
  }

  static void initializeBookOnDisk(Path bookPath) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertInitializedAtRow(database);
          insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
          insertAccountRow(database, "2000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
        });
  }

  static void deactivateAccount(Path bookPath, String accountCode) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database ->
            database.executeStatement(
                """
                update account
                set active = 0
                where account_code = '%s'
                """
                    .formatted(accountCode)));
  }

  static void withStandaloneDatabase(BookAccess bookAccess, SqliteDatabaseAction action) {
    try (SqliteNativeDatabase database = SqliteNativeConnections.open(bookAccess)) {
      action.run(database);
    }
  }

  static <T> T withStandaloneDatabaseResult(BookAccess bookAccess, SqliteDatabaseQuery<T> query) {
    try (SqliteNativeDatabase database = SqliteNativeConnections.open(bookAccess)) {
      return query.run(database);
    }
  }

  static BookAccess staticBookAccess(Path bookPath) {
    try {
      Path keyDirectory = Files.createTempDirectory("fingrind-book-key-");
      Path keyPath = keyDirectory.resolve("book.key");
      keyDirectory.toFile().deleteOnExit();
      keyPath.toFile().deleteOnExit();
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    if (Files.notExists(keyPath)) {
      SqliteBookKeyFileGenerator.generate(keyPath);
    } else {
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);
    }
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
  }

  /** Checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database);
  }

  /** Checked query against a temporary native SQLite handle. */
  @FunctionalInterface
  interface SqliteDatabaseQuery<T> {
    T run(SqliteNativeDatabase database);
  }

  /** Snapshot of one probed SQLite book-state helper result set. */
  record BookStateProbe(
      boolean hasCanonicalTables, boolean hasInitializedMarker, String bookState) {}

  /** One expected drift between enforced PRAGMA state and an injected hardening mutation. */
  record OpenConfigurationDrift(String pragma, String failureMessage) {}
}
