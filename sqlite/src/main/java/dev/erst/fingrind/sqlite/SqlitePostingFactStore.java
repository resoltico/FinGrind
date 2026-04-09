package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** SQLite-backed runtime store that shells out to a pinned sqlite3 CLI for one single-book file. */
public final class SqlitePostingFactStore implements PostingFactStore, AutoCloseable {
  static final String SQLITE3_BINARY_ENV = "FINGRIND_SQLITE3_BINARY";
  private static final String SQLITE3_BINARY = "sqlite3";
  private static final String SESSION_PREAMBLE =
      """
        pragma foreign_keys = on;
        """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path bookPath;

  /** Opens a SQLite book file and initializes the FinGrind bootstrap schema. */
  public SqlitePostingFactStore(Path bookPath) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath();
    ensureParentDirectory(this.bookPath);
    executeScript(readSchema(SqlitePostingFactStore::openSchemaStream));
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    JsonNode postingRows =
        executeQuery(
            """
            select
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
                correction_kind,
                prior_posting_id
            from posting_fact
            where idempotency_key = %s
            """
                .formatted(sqlLiteral(idempotencyKey.value())));
    if (postingRows.isEmpty()) {
      return Optional.empty();
    }
    JsonNode postingRow = postingRows.get(0);
    PostingId postingId = new PostingId(requiredText(postingRow, "posting_id"));
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse(requiredText(postingRow, "effective_date")),
            loadLines(postingId),
            readCorrectionReference(postingRow));
    ProvenanceEnvelope provenance =
        new ProvenanceEnvelope(
            requiredText(postingRow, "actor_id"),
            ProvenanceEnvelope.ActorType.valueOf(requiredText(postingRow, "actor_type")),
            requiredText(postingRow, "command_id"),
            new IdempotencyKey(requiredText(postingRow, "idempotency_key")),
            requiredText(postingRow, "causation_id"),
            optionalText(postingRow, "correlation_id"),
            Instant.parse(requiredText(postingRow, "recorded_at")),
            optionalText(postingRow, "reason"),
            ProvenanceEnvelope.SourceChannel.valueOf(requiredText(postingRow, "source_channel")));
    return Optional.of(new PostingFact(postingId, journalEntry, provenance));
  }

  @Override
  public PostingFact commit(PostingFact postingFact) {
    executeScript(commitScript(postingFact));
    return postingFact;
  }

  @Override
  public void close() {
    // This adapter opens a fresh sqlite3 process per call, so there is no persistent resource to
    // release.
  }

  private List<JournalLine> loadLines(PostingId postingId) {
    JsonNode lineRows =
        executeQuery(
            """
            select account_code, entry_side, currency_code, amount
            from journal_line
            where posting_id = %s
            order by line_order
            """
                .formatted(sqlLiteral(postingId.value())));
    List<JournalLine> lines = new ArrayList<>();
    for (JsonNode lineRow : lineRows) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineRow, "account_code")),
              JournalLine.EntrySide.valueOf(requiredText(lineRow, "entry_side")),
              new Money(
                  new CurrencyCode(requiredText(lineRow, "currency_code")),
                  new BigDecimal(requiredText(lineRow, "amount")))));
    }
    return lines;
  }

  static Optional<CorrectionReference> readCorrectionReference(JsonNode postingRow) {
    Optional<String> correctionKind = optionalText(postingRow, "correction_kind");
    Optional<String> priorPostingId = optionalText(postingRow, "prior_posting_id");
    if (correctionKind.isEmpty() || priorPostingId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new CorrectionReference(
            CorrectionReference.CorrectionKind.valueOf(correctionKind.orElseThrow()),
            new PostingId(priorPostingId.orElseThrow())));
  }

  private JsonNode executeQuery(String querySql) {
    SqliteCommandResult result =
        runSqlite3(List.of("-batch", "-bail", "-json", bookPath.toString()), querySql);
    return objectMapper.readTree(result.standardOutput());
  }

  private void executeScript(String sqlScript) {
    runSqlite3(List.of("-batch", "-bail", bookPath.toString()), sqlScript);
  }

  private SqliteCommandResult runSqlite3(List<String> arguments, String sql) {
    return runSqlite3(arguments, sql, command -> new ProcessBuilder(command).start());
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  static SqliteCommandResult runSqlite3(
      List<String> arguments, String sql, ProcessStarter processStarter) {
    List<String> command = sqlite3Command(System.getenv(SQLITE3_BINARY_ENV));
    command.addAll(arguments);
    Process process;
    try {
      process = processStarter.start(command);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to start sqlite3.", exception);
    }
    try (OutputStreamWriter writer =
        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
      writer.write(SESSION_PREAMBLE);
      writer.write(sql);
      if (!sql.endsWith(System.lineSeparator())) {
        writer.write(System.lineSeparator());
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to write sqlite3 input.", exception);
    }
    String standardOutput = readStream(process.getInputStream(), "stdout");
    String standardError = readStream(process.getErrorStream(), "stderr");
    int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for sqlite3.", exception);
    }
    if (exitCode != 0) {
      throw new IllegalStateException("sqlite3 failed: " + standardError.strip());
    }
    return new SqliteCommandResult(standardOutput, standardError);
  }

  static String sqlite3Binary(String overrideBinary) {
    if (overrideBinary == null || overrideBinary.isBlank()) {
      return SQLITE3_BINARY;
    }
    return overrideBinary;
  }

  static List<String> sqlite3Command(String overrideBinary) {
    String configuredBinary = sqlite3Binary(overrideBinary);
    if (configuredBinary.endsWith(".sh")) {
      return new ArrayList<>(List.of("bash", configuredBinary));
    }
    return new ArrayList<>(List.of(configuredBinary));
  }

  private static String readStream(java.io.InputStream inputStream, String streamName) {
    try {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read sqlite3 " + streamName + ".", exception);
    }
  }

  static String readSchema(Supplier<InputStream> schemaStreamSupplier) {
    try {
      return new String(
          Objects.requireNonNull(
                  schemaStreamSupplier.get(), "SQLite bootstrap schema resource is missing.")
              .readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read SQLite bootstrap schema.", exception);
    }
  }

  private static InputStream openSchemaStream() {
    return SqlitePostingFactStore.class.getResourceAsStream(
        "/dev/erst/fingrind/sqlite/V1__bootstrap.sql");
  }

  private static void ensureParentDirectory(Path bookPath) {
    Path parent = Objects.requireNonNull(bookPath.getParent(), "Book path parent is missing.");
    try {
      Files.createDirectories(parent);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create SQLite book directory.", exception);
    }
  }

  private static String commitScript(PostingFact postingFact) {
    StringBuilder script =
        new StringBuilder(2048)
            .append("begin immediate;\n")
            .append(
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
                correction_kind,
                prior_posting_id
            ) values (
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s,
                %s
            );
            """
                    .formatted(
                        sqlLiteral(postingFact.postingId().value()),
                        sqlLiteral(postingFact.journalEntry().effectiveDate().toString()),
                        sqlLiteral(postingFact.provenance().recordedAt().toString()),
                        sqlLiteral(postingFact.provenance().actorId()),
                        sqlLiteral(postingFact.provenance().actorType().name()),
                        sqlLiteral(postingFact.provenance().commandId()),
                        sqlLiteral(postingFact.provenance().idempotencyKey().value()),
                        sqlLiteral(postingFact.provenance().causationId()),
                        sqlLiteral(postingFact.provenance().correlationId().orElse(null)),
                        sqlLiteral(postingFact.provenance().reason().orElse(null)),
                        sqlLiteral(postingFact.provenance().sourceChannel().name()),
                        sqlLiteral(
                            postingFact
                                .journalEntry()
                                .correctionReference()
                                .map(reference -> reference.kind().name())
                                .orElse(null)),
                        sqlLiteral(
                            postingFact
                                .journalEntry()
                                .correctionReference()
                                .map(reference -> reference.priorPostingId().value())
                                .orElse(null))));
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      script.append(
          """
                insert into journal_line (
                    posting_id,
                    line_order,
                    account_code,
                    entry_side,
                    currency_code,
                    amount
                ) values (
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                );
                """
              .formatted(
                  sqlLiteral(postingFact.postingId().value()),
                  Integer.toString(index),
                  sqlLiteral(line.accountCode().value()),
                  sqlLiteral(line.side().name()),
                  sqlLiteral(line.amount().currencyCode().value()),
                  sqlLiteral(line.amount().amount().toPlainString())));
    }
    script.append("commit;\n");
    return script.toString();
  }

  private static String requiredText(JsonNode node, String fieldName) {
    JsonNode fieldNode =
        Objects.requireNonNull(node.get(fieldName), "Missing sqlite3 field: " + fieldName);
    return Objects.requireNonNull(fieldNode.textValue(), "Null sqlite3 field: " + fieldName);
  }

  static Optional<String> optionalText(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(fieldNode.textValue());
  }

  private static String sqlLiteral(String value) {
    if (value == null) {
      return "null";
    }
    return "'" + value.replace("'", "''") + "'";
  }

  /** Starts one sqlite3 process for the supplied command line. */
  @FunctionalInterface
  interface ProcessStarter {
    /** Launches one process for the supplied command line. */
    Process start(List<String> command) throws IOException;
  }

  /** Captured stdout and stderr from one sqlite3 process invocation. */
  record SqliteCommandResult(String standardOutput, String standardError) {}
}
