package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.runtime.PostingCommitResult;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed runtime store that keeps one in-process database handle per opened book.
 *
 * <p>This store is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
public final class SqlitePostingFactStore implements PostingFactStore, AutoCloseable {
  private final Path bookPath;

  private SqliteNativeDatabase database;
  private boolean closed;
  private boolean schemaInitialized;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  public SqlitePostingFactStore(Path bookPath) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath();
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    return findOnePosting(
        SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
        statement -> statement.bindText(1, idempotencyKey.value()));
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    return findOnePosting(
        SqlitePostingSql.FIND_POSTING_BY_ID, statement -> statement.bindText(1, postingId.value()));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    ensureOpen();
    if (Files.notExists(bookPath)) {
      return Optional.empty();
    }
    return findOnePosting(
        SqlitePostingSql.FIND_REVERSAL_FOR,
        statement -> statement.bindText(1, priorPostingId.value()));
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    ensureOpen();
    SqliteNativeDatabase writableDatabase = writableDatabase();
    initializeSchemaIfNeeded(writableDatabase);
    try {
      writableDatabase.executeStatement("begin immediate");
      Optional<PostingCommitResult> ordinaryOutcome =
          ordinaryOutcomeBeforeInsert(writableDatabase, postingFact);
      if (ordinaryOutcome.isPresent()) {
        rollbackQuietly(writableDatabase);
        return ordinaryOutcome.orElseThrow();
      }
      insertPostingFact(writableDatabase, postingFact);
      insertJournalLines(writableDatabase, postingFact);
      writableDatabase.executeStatement("commit");
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      rollbackQuietly(writableDatabase);
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
      schemaInitialized = false;
    }
  }

  private Optional<PostingCommitResult> ordinaryOutcomeBeforeInsert(
      SqliteNativeDatabase activeDatabase, PostingFact postingFact) throws SqliteNativeException {
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

  private Optional<PostingFact> findOnePosting(String sql, SqliteBinder binder) {
    return findOnePosting(database(), sql, binder);
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

  private boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, SqliteBinder binder)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(activeDatabase, sql)) {
      binder.bind(statement);
      return statement.step() == SqliteNativeLibrary.SQLITE_ROW;
    }
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        SqliteNativeLibrary.prepare(activeDatabase, SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
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

  private void initializeSchemaIfNeeded(SqliteNativeDatabase writableDatabase) {
    if (schemaInitialized) {
      return;
    }
    try {
      // Schema bootstrap is intentionally separate from the posting transaction. The canonical
      // script is idempotent book initialization, not part of one accounting fact commit.
      SqliteSchemaManager.initializeBook(writableDatabase);
      schemaInitialized = true;
    } catch (SqliteNativeException exception) {
      throw sqliteFailure("Failed to initialize SQLite book schema.", exception);
    }
  }

  private SqliteNativeDatabase writableDatabase() {
    SqliteSchemaManager.ensureParentDirectory(bookPath);
    return database();
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
      // Preserve the original failure or ordinary duplicate outcome.
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
