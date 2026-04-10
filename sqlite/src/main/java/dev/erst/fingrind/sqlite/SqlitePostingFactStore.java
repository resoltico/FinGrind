package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.runtime.PostingCommitResult;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** SQLite-backed runtime store that shells out to a pinned sqlite3 CLI for one single-book file. */
public final class SqlitePostingFactStore implements PostingFactStore, AutoCloseable {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path bookPath;
  private final SqliteCommandExecutor commandExecutor;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  public SqlitePostingFactStore(Path bookPath) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath();
    this.commandExecutor = new SqliteCommandExecutor(this.bookPath);
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    return findOnePosting(SqlitePostingSql.findPostingByIdempotency(idempotencyKey));
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    return findOnePosting(SqlitePostingSql.findPostingById(postingId));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return findOnePosting(SqlitePostingSql.findReversalFor(priorPostingId));
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    SqliteSchemaManager.initializeBook(bookPath, commandExecutor);
    SqliteCommandExecutor.SqliteCommandResult result =
        commandExecutor.script(SqlitePostingSql.commitScript(postingFact));
    if (result.exitCode() == 0) {
      return new PostingCommitResult.Committed(postingFact);
    }
    String standardError = result.standardError().strip();
    if (standardError.contains("UNIQUE constraint failed: posting_fact.idempotency_key")) {
      return new PostingCommitResult.DuplicateIdempotency(
          postingFact.provenance().requestProvenance().idempotencyKey());
    }
    if (standardError.contains("UNIQUE constraint failed: posting_fact.prior_posting_id")) {
      return new PostingCommitResult.DuplicateReversalTarget(
          postingFact.correctionReference().orElseThrow().priorPostingId());
    }
    throw new IllegalStateException("sqlite3 failed: " + standardError);
  }

  @Override
  public void close() {
    // This adapter opens a fresh sqlite3 process per call, so there is no persistent resource to
    // release.
  }

  private Optional<PostingFact> findOnePosting(String postingQuery) {
    if (!Files.exists(bookPath)) {
      return Optional.empty();
    }
    JsonNode postingRows = executeQuery(postingQuery);
    if (postingRows.isEmpty()) {
      return Optional.empty();
    }
    JsonNode postingRow = postingRows.get(0);
    PostingId postingId = new PostingId(SqlitePostingMapper.requiredText(postingRow, "posting_id"));
    return Optional.of(SqlitePostingMapper.postingFact(postingRow, loadLines(postingId)));
  }

  private java.util.List<dev.erst.fingrind.core.JournalLine> loadLines(PostingId postingId) {
    return SqlitePostingMapper.journalLines(executeQuery(SqlitePostingSql.loadLines(postingId)));
  }

  private JsonNode executeQuery(String querySql) {
    SqliteCommandExecutor.SqliteCommandResult result = commandExecutor.query(querySql);
    if (result.exitCode() != 0) {
      throw new IllegalStateException("sqlite3 failed: " + result.standardError().strip());
    }
    return objectMapper.readTree(result.standardOutput());
  }
}
