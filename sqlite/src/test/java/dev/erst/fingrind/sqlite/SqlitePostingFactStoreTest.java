package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqlitePostingFactStoreTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @TempDir Path tempDirectory;

  @Test
  void findByIdempotency_returnsEmptyWhenPostingIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      assertEquals(
          Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));
    }
  }

  @Test
  void commitAndFindByIdempotency_returnsStoredFactWithoutCorrection() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact = postingFact("posting-1", "idem-1", Optional.empty());

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
    }
  }

  @Test
  void commitAndFindByIdempotency_preservesCorrectionReference() {
    Path databasePath = tempDirectory.resolve("nested").resolve("entity-b.sqlite");
    PostingFact postingFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(
                new CorrectionReference(
                    CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))));

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-2")));
    }
  }

  @Test
  void commit_rejectsDuplicateIdempotencyKey() {
    Path databasePath = tempDirectory.resolve("fingrind.sqlite");
    PostingFact postingFact = postingFact("posting-1", "idem-1", Optional.empty());

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(postingFact);

      assertThrows(
          IllegalStateException.class,
          () -> postingFactStore.commit(postingFact("posting-2", "idem-1", Optional.empty())));
    }
  }

  @Test
  void readCorrectionReference_returnsEmptyWhenOnlyOneColumnIsPresent() throws IOException {
    var postingRow =
        OBJECT_MAPPER.readTree(
            """
            {
              "correction_kind": "AMENDMENT"
            }
            """);

    assertEquals(Optional.empty(), SqlitePostingFactStore.readCorrectionReference(postingRow));
  }

  @Test
  void constructor_rejectsBookPathWhoseParentIsAFile() throws IOException {
    Path fileParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(fileParent, "nope", StandardCharsets.UTF_8);

    assertThrows(
        IllegalStateException.class,
        () -> new SqlitePostingFactStore(fileParent.resolve("entity.sqlite")));
  }

  @Test
  void runSqlite3_appendsATrailingNewlineWhenSqlDoesNotEndWithOne() {
    ByteArrayOutputStream stdinCapture = new ByteArrayOutputStream();
    StubProcess process =
        new StubProcess(
            stdinCapture,
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayInputStream(new byte[0]),
            0,
            null);

    SqlitePostingFactStore.runSqlite3(List.of(":memory:"), "select 1;", command -> process);

    String writtenSql = stdinCapture.toString(StandardCharsets.UTF_8);
    assertTrue(writtenSql.contains("pragma foreign_keys = on;"));
    assertTrue(writtenSql.endsWith(System.lineSeparator()));
  }

  @Test
  void runSqlite3_mapsProcessStartFailure() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqlitePostingFactStore.runSqlite3(
                List.of(":memory:"),
                "select 1;\n",
                command -> {
                  throw new IOException("boom");
                }));
  }

  @Test
  void sqlite3Binary_prefersConfiguredOverrideWhenPresent() {
    assertEquals(
        "/tmp/pinned-sqlite3", SqlitePostingFactStore.sqlite3Binary("/tmp/pinned-sqlite3"));
  }

  @Test
  void sqlite3Binary_fallsBackToDefaultWhenOverrideIsBlank() {
    assertEquals("sqlite3", SqlitePostingFactStore.sqlite3Binary("  "));
  }

  @Test
  void sqlite3Binary_fallsBackToDefaultWhenOverrideIsNull() {
    assertEquals("sqlite3", SqlitePostingFactStore.sqlite3Binary(null));
  }

  @Test
  void sqlite3Command_wrapsShellScriptOverridesThroughBash() {
    assertEquals(
        List.of("bash", "/tmp/sqlite3.sh"),
        SqlitePostingFactStore.sqlite3Command("/tmp/sqlite3.sh"));
  }

  @Test
  void sqlite3Command_usesConfiguredBinaryDirectlyWhenOverrideIsNotAScript() {
    assertEquals(
        List.of("/tmp/pinned-sqlite3"),
        SqlitePostingFactStore.sqlite3Command("/tmp/pinned-sqlite3"));
  }

  @Test
  void runSqlite3_mapsWriteFailure() {
    StubProcess process =
        new StubProcess(
            new OutputStream() {
              @Override
              public void write(int value) throws IOException {
                throw new IOException("boom");
              }
            },
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayInputStream(new byte[0]),
            0,
            null);

    assertThrows(
        IllegalStateException.class,
        () ->
            SqlitePostingFactStore.runSqlite3(
                List.of(":memory:"), "select 1;\n", command -> process));
  }

  @Test
  void runSqlite3_mapsInterruptedWaitAndRestoresTheInterruptFlag() {
    StubProcess process =
        new StubProcess(
            new ByteArrayOutputStream(),
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayInputStream(new byte[0]),
            0,
            new InterruptedException("boom"));

    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              SqlitePostingFactStore.runSqlite3(
                  List.of(":memory:"), "select 1;\n", command -> process));
      assertTrue(Thread.interrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void runSqlite3_mapsStdoutReadFailure() {
    StubProcess process =
        new StubProcess(
            new ByteArrayOutputStream(),
            failingInputStream(),
            new ByteArrayInputStream(new byte[0]),
            0,
            null);

    assertThrows(
        IllegalStateException.class,
        () ->
            SqlitePostingFactStore.runSqlite3(
                List.of(":memory:"), "select 1;\n", command -> process));
  }

  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqlitePostingFactStore.readSchema(SqlitePostingFactStoreTest::failingInputStream));
  }

  private static InputStream failingInputStream() {
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

  private static PostingFact postingFact(
      String postingId, String idempotencyKey, Optional<CorrectionReference> correctionReference) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")))),
            correctionReference),
        new ProvenanceEnvelope(
            "actor-1",
            ProvenanceEnvelope.ActorType.AGENT,
            "command-" + postingId,
            new IdempotencyKey(idempotencyKey),
            "cause-1",
            Optional.of("corr-1"),
            Instant.parse("2026-04-07T10:15:30Z"),
            Optional.of("reason-1"),
            ProvenanceEnvelope.SourceChannel.CLI));
  }

  /** Stub process used to exercise sqlite3 process failure handling deterministically. */
  private static final class StubProcess extends Process {
    private final OutputStream outputStream;
    private final InputStream inputStream;
    private final InputStream errorStream;
    private final int exitCode;
    private final InterruptedException interruptedException;

    private StubProcess(
        OutputStream outputStream,
        InputStream inputStream,
        InputStream errorStream,
        int exitCode,
        InterruptedException interruptedException) {
      this.outputStream = outputStream;
      this.inputStream = inputStream;
      this.errorStream = errorStream;
      this.exitCode = exitCode;
      this.interruptedException = interruptedException;
    }

    @Override
    public OutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public InputStream getInputStream() {
      return inputStream;
    }

    @Override
    public InputStream getErrorStream() {
      return errorStream;
    }

    @Override
    public int waitFor() throws InterruptedException {
      if (interruptedException != null) {
        throw interruptedException;
      }
      return exitCode;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {
      // Nothing to release for the test double.
    }
  }
}
