package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.BookSession;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostingCommitResult;
import dev.erst.fingrind.application.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements BookSession {
  private static final String ACCOUNT_TABLE = "account";
  private static final String BOOK_META_TABLE = "book_meta";
  private static final String POSTING_FACT_TABLE = "posting_fact";

  private final Path bookPath;

  private SqliteNativeDatabase database;
  private boolean closed;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  public SqlitePostingFactStore(Path bookPath) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath();
  }

  @Override
  public boolean isInitialized() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return false;
    }
    try {
      return isInitialized(database());
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public OpenBookResult openBook(Instant initializedAt) {
    ensureOpen();
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    SqliteNativeDatabase writableDatabase = database();
    try {
      if (isInitialized(writableDatabase)) {
        return new OpenBookResult.Rejected(
            new BookAdministrationRejection.BookAlreadyInitialized());
      }
      if (hasUserSchemaObjects(writableDatabase)) {
        return new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema());
      }

      writableDatabase.executeStatement("begin immediate");
      SqliteSchemaManager.initializeBook(writableDatabase);
      insertInitializedAt(writableDatabase, initializedAt);
      writableDatabase.executeStatement("commit");
      return new OpenBookResult.Opened(initializedAt);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(writableDatabase);
      throw sqliteFailure("Failed to initialize SQLite book.", exception);
    }
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (!existsTable(activeDatabase, ACCOUNT_TABLE)) {
        return Optional.empty();
      }
      return findOneAccount(activeDatabase, accountCode);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new DeclareAccountResult.Rejected(
          new BookAdministrationRejection.BookNotInitialized());
    }
    SqliteNativeDatabase activeDatabase = database();
    try {
      if (!isInitialized(activeDatabase)) {
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.BookNotInitialized());
      }

      activeDatabase.executeStatement("begin immediate");
      Optional<DeclaredAccount> existingAccount = findOneAccount(activeDatabase, accountCode);
      if (existingAccount.isPresent()
          && existingAccount.orElseThrow().normalBalance() != normalBalance) {
        rollbackQuietly(activeDatabase);
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.NormalBalanceConflict(
                accountCode, existingAccount.orElseThrow().normalBalance(), normalBalance));
      }

      DeclaredAccount declaredAccount =
          existingAccount
              .map(
                  account ->
                      new DeclaredAccount(
                          account.accountCode(),
                          accountName,
                          account.normalBalance(),
                          true,
                          account.declaredAt()))
              .orElseGet(
                  () ->
                      new DeclaredAccount(
                          accountCode, accountName, normalBalance, true, declaredAt));
      upsertAccount(activeDatabase, declaredAccount);
      activeDatabase.executeStatement("commit");
      return new DeclareAccountResult.Declared(declaredAccount);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(activeDatabase);
      throw sqliteFailure("Failed to declare SQLite book account.", exception);
    }
  }

  @Override
  public List<DeclaredAccount> listAccounts() {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return List.of();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (!existsTable(activeDatabase, ACCOUNT_TABLE)) {
        return List.of();
      }
      return loadAccounts(activeDatabase);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (!existsTable(activeDatabase, POSTING_FACT_TABLE)) {
        return Optional.empty();
      }
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (!existsTable(activeDatabase, POSTING_FACT_TABLE)) {
        return Optional.empty();
      }
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_ID,
          statement -> statement.bindText(1, postingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    try {
      SqliteNativeDatabase activeDatabase = database();
      if (!existsTable(activeDatabase, POSTING_FACT_TABLE)) {
        return Optional.empty();
      }
      return findOnePosting(
          activeDatabase,
          SqlitePostingSql.FIND_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()));
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return new PostingCommitResult.BookNotInitialized();
    }
    SqliteNativeDatabase activeDatabase = database();
    try {
      activeDatabase.executeStatement("begin immediate");
      Optional<PostingCommitResult> ordinaryOutcome =
          ordinaryOutcomeBeforeInsert(activeDatabase, postingFact);
      if (ordinaryOutcome.isPresent()) {
        rollbackQuietly(activeDatabase);
        return ordinaryOutcome.orElseThrow();
      }
      insertPostingFact(activeDatabase, postingFact);
      insertJournalLines(activeDatabase, postingFact);
      activeDatabase.executeStatement("commit");
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(activeDatabase);
      throw sqliteFailure("Failed to commit SQLite posting fact.", exception);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (database == null) {
      return;
    }
    try {
      database.close();
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to close SQLite book connection.", exception);
    } finally {
      database = null;
    }
  }

  private Optional<PostingCommitResult> ordinaryOutcomeBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingFact postingFact) throws SqliteNativeException {
    if (!isInitialized(activeDatabase)) {
      return Optional.of(new PostingCommitResult.BookNotInitialized());
    }

    Optional<PostingCommitResult> accountOutcome =
        accountOutcomeBeforeInsert(activeDatabase, postingFact);
    if (accountOutcome.isPresent()) {
      return accountOutcome;
    }

    if (existsRow(
        activeDatabase,
        SqlitePostingSql.EXISTS_POSTING_BY_IDEMPOTENCY,
        statement ->
            statement.bindText(
                1, postingFact.provenance().requestProvenance().idempotencyKey().value()))) {
      return Optional.of(
          new PostingCommitResult.DuplicateIdempotency(
              postingFact.provenance().requestProvenance().idempotencyKey()));
    }

    ReversalReference reversalReference = postingFact.reversalReference().orElse(null);
    if (reversalReference != null) {
      PostingId priorPostingId = reversalReference.priorPostingId();
      if (existsRow(
          activeDatabase,
          SqlitePostingSql.EXISTS_REVERSAL_FOR,
          statement -> statement.bindText(1, priorPostingId.value()))) {
        return Optional.of(new PostingCommitResult.DuplicateReversalTarget(priorPostingId));
      }
    }
    return Optional.empty();
  }

  private Optional<PostingCommitResult> accountOutcomeBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingFact postingFact) throws SqliteNativeException {
    for (JournalLine line : postingFact.journalEntry().lines()) {
      Optional<DeclaredAccount> account = findOneAccount(activeDatabase, line.accountCode());
      if (account.isEmpty()) {
        return unknownAccount(line.accountCode());
      }
      if (!account.orElseThrow().active()) {
        return inactiveAccount(line.accountCode());
      }
    }
    return Optional.empty();
  }

  private static Optional<PostingCommitResult> unknownAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.UnknownAccount(accountCode));
  }

  private static Optional<PostingCommitResult> inactiveAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.InactiveAccount(accountCode));
  }

  private Optional<PostingFact> findOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder) {
    try {
      return executeFindOnePosting(activeDatabase, sql, binder);
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private Optional<PostingFact> executeFindOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    // This path closes a single-use statement deterministically without try-with-resources so the
    // helper stays free of compiler-generated close branches under the module's 100% coverage gate.
    SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql);
    try {
      binder.bind(statement);
      int resultCode = statement.step();
      if (resultCode == SqliteNativeLibrary.SQLITE_DONE) {
        return Optional.empty();
      }
      PostingId postingId =
          new PostingId(
              SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
      return Optional.of(
          SqlitePostingMapper.postingFact(statement, loadLines(activeDatabase, postingId)));
    } finally {
      statement.close();
    }
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private Optional<DeclaredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) throws SqliteNativeException {
    // This path closes a single-use statement deterministically without try-with-resources so the
    // helper stays free of compiler-generated close branches under the module's 100% coverage gate.
    SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.FIND_ACCOUNT_BY_CODE);
    try {
      statement.bindText(1, accountCode.value());
      if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
        return Optional.empty();
      }
      return Optional.of(SqlitePostingMapper.declaredAccount(statement));
    } finally {
      statement.close();
    }
  }

  private List<DeclaredAccount> loadAccounts(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    List<DeclaredAccount> accounts = new ArrayList<>();
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.LIST_ACCOUNTS)) {
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        accounts.add(SqlitePostingMapper.declaredAccount(statement));
      }
    }
    return accounts;
  }

  private boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql)) {
      binder.bind(statement);
      return statement.step() == SqliteNativeLibrary.SQLITE_ROW;
    }
  }

  private boolean existsTable(SqliteNativeDatabase activeDatabase, String tableName)
      throws SqliteNativeException {
    return existsRow(
        activeDatabase,
        SqlitePostingSql.TABLE_EXISTS,
        statement -> statement.bindText(1, tableName));
  }

  private boolean isInitialized(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    return existsTable(activeDatabase, BOOK_META_TABLE)
        && existsRow(
            activeDatabase,
            SqlitePostingSql.BOOK_INITIALIZED_EXISTS,
            statement -> statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY));
  }

  private boolean hasUserSchemaObjects(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return existsRow(activeDatabase, SqlitePostingSql.USER_SCHEMA_EXISTS, statement -> {});
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
      statement.bindText(2, initializedAt.toString());
      statement.step();
    }
  }

  private void upsertAccount(SqliteNativeDatabase activeDatabase, DeclaredAccount account)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      statement.bindText(2, account.accountName().value());
      statement.bindText(3, account.normalBalance().name());
      statement.bindInt(4, Boolean.compare(account.active(), false));
      statement.bindText(5, account.declaredAt().toString());
      statement.step();
    }
  }

  private void insertPostingFact(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.journalEntry().effectiveDate().toString());
      statement.bindText(3, postingFact.provenance().recordedAt().toString());
      statement.bindText(4, requestProvenance.actorId().value());
      statement.bindText(5, requestProvenance.actorType().name());
      statement.bindText(6, requestProvenance.commandId().value());
      statement.bindText(7, requestProvenance.idempotencyKey().value());
      statement.bindText(8, requestProvenance.causationId().value());
      statement.bindText(
          9, requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      statement.bindText(10, requestProvenance.reason().map(value -> value.value()).orElse(null));
      statement.bindText(11, postingFact.provenance().sourceChannel().name());
      statement.bindText(
          12,
          postingFact
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.step();
    }
  }

  private void insertJournalLines(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      try (SqliteNativeStatement statement =
          SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.INSERT_JOURNAL_LINE)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, line.accountCode().value());
        statement.bindText(4, line.side().name());
        statement.bindText(5, line.amount().currencyCode().value());
        statement.bindText(6, line.amount().amount().toPlainString());
        statement.step();
      }
    }
  }

  private SqliteNativeDatabase database() {
    if (database != null) {
      return database;
    }
    try {
      database = SqliteNativeLibrary.open(bookPath);
      database.executeStatement("pragma foreign_keys = on");
      database.executeStatement("pragma trusted_schema = off");
      return database;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to open SQLite book connection.", exception);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("SQLite book session is already closed.");
    }
  }

  private static void rollbackQuietly(SqliteNativeDatabase activeDatabase) {
    try {
      activeDatabase.executeStatement("rollback");
    } catch (SqliteNativeException ignored) {
      // Preserve the original failure or ordinary outcome.
    }
  }

  private static IllegalStateException sqliteFailure(
      String message, SqliteNativeException exception) {
    String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
    return new IllegalStateException(
        message + " " + exception.resultName() + ": " + detail, exception);
  }

  /** Binds one prepared SQLite statement before it is stepped. */
  @FunctionalInterface
  private interface SqliteBinder {
    /** Applies parameter values to one prepared SQLite statement. */
    void bind(SqliteNativeStatement statement) throws SqliteNativeException;
  }
}
