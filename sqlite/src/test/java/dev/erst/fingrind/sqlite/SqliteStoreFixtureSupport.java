package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEventKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Shared SQLite store/bootstrap fixtures and native-handle doubles for split store tests. */
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
      return Objects.requireNonNull(value, "SQLite text query returned null.");
    }
  }

  static void insertInitializedAtRow(SqliteNativeDatabase database) {
    database.executeStatement(
        """
        insert into book_meta (meta_key, value)
        values ('initialized_at', '2026-04-07T10:15:30Z')
        """);
  }

  static void insertCanonicalInitializedBookMetadata(SqliteNativeDatabase database) {
    SqliteBookIntegrityVerifier.recordSchemaFingerprint(database);
    insertInitializedAtRow(database);
    SqliteMutationWriter.insertBookIdentity(
        database, SqlitePostingFactFixtureSupport.bookIdentity());
  }

  static void insertAccountRow(
      SqliteNativeDatabase database,
      String accountCode,
      String accountName,
      String normalBalance,
      int active,
      String declaredAt) {
    insertAccountRow(
        database,
        accountCode,
        accountName,
        impliedAccountType(normalBalance).wireValue(),
        normalBalance,
        active,
        declaredAt);
  }

  static void insertAccountRow(
      SqliteNativeDatabase database,
      String accountCode,
      String accountName,
      String accountType,
      String normalBalance,
      int active,
      String declaredAt) {
    AccountTaxonomy accountTaxonomy = impliedAccountTaxonomy(accountType);
    database.executeStatement(
        """
        insert into account (
            account_code,
            account_name,
            account_type,
            account_role,
            account_node_kind,
            parent_account_code,
            financial_position_line_classification,
            profit_and_loss_line_classification,
            active,
            declared_at
        ) values (
            '%s',
            '%s',
            '%s',
            '%s',
            'POSTABLE',
            null,
            %s,
            %s,
            %d,
            '%s'
        )
        """
            .formatted(
                accountCode,
                accountName,
                accountType,
                impliedAccountRole(accountType, normalBalance),
                accountTaxonomy
                    .financialPositionLineClassification()
                    .map(FinancialPositionLineClassification::wireValue)
                    .map(SqliteStoreFixtureSupport::quotedSqlStringLiteral)
                    .orElse("null"),
                accountTaxonomy
                    .profitAndLossLineClassification()
                    .map(ProfitAndLossLineClassification::wireValue)
                    .map(SqliteStoreFixtureSupport::quotedSqlStringLiteral)
                    .orElse("null"),
                active,
                declaredAt));
  }

  private static AccountType impliedAccountType(String normalBalance) {
    return switch (normalBalance) {
      case "DEBIT" -> AccountType.ASSET;
      case "CREDIT" -> AccountType.REVENUE;
      default ->
          throw new IllegalArgumentException(
              "Unsupported fixture normal balance for implied account type: " + normalBalance);
    };
  }

  private static String impliedAccountRole(String accountType, String normalBalance) {
    AccountType parsedAccountType = AccountType.fromWireValue(accountType);
    NormalBalance parsedNormalBalance = NormalBalance.valueOf(normalBalance);
    for (AccountRole accountRole : List.of(AccountRole.ORDINARY, AccountRole.CONTRA)) {
      if (AccountSemantics.normalBalance(parsedAccountType, accountRole) == parsedNormalBalance) {
        return accountRole.wireValue();
      }
    }
    throw new IllegalArgumentException(
        "Unsupported fixture account semantics for %s/%s."
            .formatted(parsedAccountType.wireValue(), parsedNormalBalance.name()));
  }

  private static AccountTaxonomy impliedAccountTaxonomy(String accountType) {
    return SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.fromWireValue(accountType));
  }

  private static String quotedSqlStringLiteral(String value) {
    return "'" + value + "'";
  }

  static void insertJournalLineRow(
      SqliteNativeDatabase database,
      String postingId,
      int lineOrder,
      String accountCode,
      String entrySide,
      String currencyCode,
      long amountMinor) {
    database.executeStatement(
        """
        insert into journal_line (
            posting_id,
            line_order,
            account_code,
            entry_side,
            currency_code,
            amount_minor
        ) values (
            '%s',
            %d,
            '%s',
            '%s',
            '%s',
            %d
        )
        """
            .formatted(postingId, lineOrder, accountCode, entrySide, currencyCode, amountMinor));
  }

  static void insertAuditEventRow(
      SqliteNativeDatabase database,
      String recordedAt,
      String eventKind,
      String accountCodeSqlLiteral,
      String postingIdSqlLiteral) {
    database.executeStatement(
        """
        insert into audit_event (
            recorded_at,
            event_kind,
            account_code,
            posting_id
        ) values (
            '%s',
            '%s',
            %s,
            %s
        )
        """
            .formatted(recordedAt, eventKind, accountCodeSqlLiteral, postingIdSqlLiteral));
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

  /** Same-package deterministic lifecycle double that fails with one plain Java exception. */
  static final class IllegalStateClosingSqliteNativeDatabase extends SqliteNativeDatabase {
    boolean closeAttempted;

    IllegalStateClosingSqliteNativeDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    public void close() {
      closeAttempted = true;
      throw new IllegalStateException("Simulated lifecycle close failure.");
    }

    boolean closeAttempted() {
      return closeAttempted;
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
                  '%s',
                  null
              )
              """
                  .formatted(SourceChannel.CLI.wireValue()));
        });
  }

  static void createEmptySqliteFile(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), database -> {});
  }

  static void createSchemaOnlyBook(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), SqliteBookSchemaBootstrap::initializeBook);
  }

  static void createPartialFinGrindBook(
      Path bookPath, boolean includeInitializedMarker, String... omittedTables) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          if (omittedTables.length > 0) {
            database.executeStatement("pragma foreign_keys = off");
            for (String omittedTable : omittedTables) {
              database.executeStatement("drop table if exists " + omittedTable);
            }
            database.executeStatement("pragma foreign_keys = on");
          }
          if (includeInitializedMarker) {
            insertOptionalInitializedFixtureRows(database, omittedTables.length == 0);
          }
        });
  }

  private static void insertOptionalInitializedFixtureRows(
      SqliteNativeDatabase database, boolean includeCanonicalFixtureRows) {
    if (tableExists(database, SqliteBookContract.BOOK_META_TABLE)) {
      insertInitializedAtRow(database);
      if (includeCanonicalFixtureRows) {
        SqliteBookIntegrityVerifier.recordSchemaFingerprint(database);
      }
    }
    if (!includeCanonicalFixtureRows) {
      return;
    }
    if (tableExists(database, SqliteBookContract.BOOK_IDENTITY_TABLE)
        && tableExists(database, SqliteBookContract.ENTITY_PROFILE_TABLE)
        && tableExists(database, SqliteBookContract.BOOK_POLICY_TABLE)) {
      SqliteMutationWriter.insertBookIdentity(
          database, SqlitePostingFactFixtureSupport.bookIdentity());
    }
    if (tableExists(database, SqliteBookContract.AUDIT_EVENT_TABLE)) {
      insertAuditEventRow(
          database,
          "2026-04-07T10:15:30Z",
          BookAuditEventKind.BOOK_OPENED.wireValue(),
          "null",
          "null");
    }
    if (tableExists(database, SqliteBookContract.ACCOUNT_TABLE)) {
      insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
      insertAccountRow(database, "2000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
    }
  }

  static void initializeBookOnDisk(Path bookPath) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertAuditEventRow(
              database,
              "2026-04-07T10:15:30Z",
              BookAuditEventKind.BOOK_OPENED.wireValue(),
              "null",
              "null");
          insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
          insertAccountRow(database, "2000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
        });
  }

  private static boolean tableExists(SqliteNativeDatabase database, String tableName) {
    return SqliteStatementQueries.existsRow(
        database, SqlitePostingSql.TABLE_EXISTS, statement -> statement.bindText(1, tableName));
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
    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess)) {
      action.run(database);
    }
  }

  static <T> T withStandaloneDatabaseResult(BookAccess bookAccess, SqliteDatabaseQuery<T> query) {
    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess)) {
      return query.run(database);
    }
  }

  static BookAccess staticBookAccess(Path bookPath) {
    try {
      Path keyDirectory =
          SqliteTestPrivateDirectorySupport.createOwnerOnlyTempDirectory("fingrind-book-key-");
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

  static SqlitePostingFactStore openStore(BookAccess bookAccess) {
    return openStore(bookAccess, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  static SqlitePostingFactStore openStore(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
    return new SqlitePostingFactStore(
        bookAccess.bookFilePath(), loadPassphrase(bookAccess), accessMode);
  }

  static SqliteNativeDatabase openNativeDatabase(BookAccess bookAccess) {
    return SqliteNativeConnections.openKeyFileAccess(
        bookAccess.bookFilePath(), requireKeyFilePath(bookAccess));
  }

  static SqliteBookPassphrase loadPassphrase(BookAccess bookAccess) {
    return SqliteBookKeyFile.loadDecision(requireKeyFilePath(bookAccess))
        .fold(
            resolvedPassphrase -> resolvedPassphrase,
            failure -> {
              throw new ContractFailureException(failure);
            });
  }

  static Path requireKeyFilePath(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput source ->
          throw new IllegalArgumentException(
              "Test SQLite key-file helpers do not accept " + source.optionName() + ".");
      case BookAccess.PassphraseSource.InteractivePrompt source ->
          throw new IllegalArgumentException(
              "Test SQLite key-file helpers do not accept " + source.optionName() + ".");
    };
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
