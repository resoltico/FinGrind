package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.runtime.PostingCommitResult;
import dev.erst.fingrind.runtime.PostingFact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqlitePostingFactStoreTest {
  @TempDir Path tempDirectory;

  @Test
  void findByIdempotency_returnsEmptyWhenPostingIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      assertEquals(
          Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-posting.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      assertEquals(Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findReversalFor_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void commitAndFinders_roundTripPostingWithoutReversal() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      assertEquals(
          new PostingCommitResult.Committed(postingFact), postingFactStore.commit(postingFact));
      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
      assertEquals(
          Optional.of(postingFact), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commit_initializesCanonicalTablesAsStrict() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
    }

    SqliteNativeDatabase database = SqliteNativeLibrary.open(databasePath);
    try {
      assertEquals(
          1,
          queryInt(
              database,
              "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'"));
      assertEquals(
          1,
          queryInt(
              database,
              "select strict from pragma_table_list('journal_line') where name = 'journal_line'"));
    } finally {
      database.close();
    }
  }

  @Test
  void commit_configuresOpenConnectionForForeignKeysAndUntrustedSchema() throws Exception {
    Path databasePath = tempDirectory.resolve("connection-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      SqliteNativeDatabase activeDatabase = storeDatabase(postingFactStore);
      assertEquals(1, queryInt(activeDatabase, "pragma foreign_keys"));
      assertEquals(0, queryInt(activeDatabase, "pragma trusted_schema"));
    }
  }

  @Test
  void canonicalStrictSchema_rejectsNonLosslessTypeMismatches() throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("strict-datatype.sqlite");
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);

    try {
      SqliteSchemaManager.initializeBook(database);
      insertPostingFactRow(database, "posting-1", "idem-1");

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  database.executeStatement(
                      """
                      insert into journal_line (
                          posting_id,
                          line_order,
                          account_code,
                          entry_side,
                          currency_code,
                          amount
                      ) values (
                          'posting-1',
                          'not-an-integer',
                          '1000',
                          'DEBIT',
                          'EUR',
                          '10.00'
                      )
                      """));

      assertEquals(SqliteNativeLibrary.SQLITE_CONSTRAINT_DATATYPE, exception.resultCode());
      assertEquals("SQLITE_CONSTRAINT_DATATYPE", exception.resultName());
      assertEquals(0, queryInt(database, "select count(*) from journal_line"));
    } finally {
      database.close();
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenExistingBookHasNoMatchingPosting() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-missing")));
    }
  }

  @Test
  void commitAndFindByIdempotency_preservesReversalReference() {
    Path databasePath = tempDirectory.resolve("nested").resolve("entity-b.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact reversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(originalFact);
      postingFactStore.commit(reversalFact);

      assertEquals(
          Optional.of(reversalFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-2")));
    }
  }

  @Test
  void commit_returnsDuplicateIdempotencyOutcome() {
    Path databasePath = tempDirectory.resolve("fingrind.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(postingFact);

      assertEquals(
          new PostingCommitResult.DuplicateIdempotency(new IdempotencyKey("idem-1")),
          postingFactStore.commit(
              postingFact("posting-2", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commit_returnsDuplicateReversalTargetOutcome() {
    Path databasePath = tempDirectory.resolve("reversal.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact firstReversal =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));
    PostingFact secondReversal =
        postingFact(
            "posting-3",
            "idem-3",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("another full reversal")));

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(originalFact);
      postingFactStore.commit(firstReversal);

      assertEquals(
          new PostingCommitResult.DuplicateReversalTarget(new PostingId("posting-1")),
          postingFactStore.commit(secondReversal));
      assertEquals(
          Optional.of(firstReversal), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commit_throwsWhenPostingIdAlreadyExistsWithDifferentIdempotencyKey() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact("posting-1", "idem-2", Optional.empty(), Optional.empty())));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  void commit_throwsWhenSqliteFailureIsNotMappedToAnOrdinaryOutcome() {
    Path databasePath = tempDirectory.resolve("unexpected.sqlite");
    PostingFact invalidReversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-missing"))),
            Optional.of(new ReversalReason("operator reversal")));

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.commit(invalidReversalFact));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      assertTrue(exception.getMessage().contains("FOREIGN KEY"));
    }
  }

  @Test
  void commit_rejectsBookPathWhoseParentIsAFile() throws IOException {
    Path fileParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(fileParent, "nope", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(fileParent.resolve("entity.sqlite"))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      assertTrue(exception.getMessage().contains("Failed to create SQLite book directory."));
    }
  }

  @Test
  void findByIdempotency_throwsWhenExistingBookFileIsNotSqlite() throws IOException {
    Path databasePath = tempDirectory.resolve("not-a-database.sqlite");
    Files.writeString(databasePath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  void findByIdempotency_throwsWhenBookPathPointsAtDirectory() throws IOException {
    Path databasePath = tempDirectory.resolve("book-directory");
    Files.createDirectories(databasePath);

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));

      assertTrue(exception.getMessage().contains("Failed to open SQLite book connection."));
    }
  }

  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteSchemaManager.readSchema(SqlitePostingFactStoreTest::failingInputStream));
  }

  @Test
  void initializeBook_executesWholeSchemaScriptWithoutStatementSplitting()
      throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("schema-script.sqlite");
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);

    try {
      SqliteSchemaManager.initializeBook(
          database,
          () ->
              new ByteArrayInputStream(
                  """
                  create table sample (
                      id integer primary key,
                      note text not null
                  );
                  create table sample_audit (
                      note text not null
                  );
                  -- comment with semicolon;
                  create trigger sample_after_insert
                  after insert on sample
                  begin
                      insert into sample_audit (note) values ('semi;colon');
                  end;
                  """
                      .getBytes(StandardCharsets.UTF_8)));

      database.executeStatement("insert into sample (id, note) values (1, 'ok')");

      try (SqliteNativeStatement statement =
          SqliteNativeLibrary.prepare(database, "select note from sample_audit")) {
        assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
        assertEquals("semi;colon", statement.columnText(0));
        assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
      }
    } finally {
      database.close();
    }
  }

  @Test
  void cachedValue_loadsAndStoresValueWhenCacheIsEmpty() {
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals("loaded", SqliteSchemaManager.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<String> schemaCache = new AtomicReference<>("cached");

    assertEquals(
        "cached",
        SqliteSchemaManager.cachedValue(
            schemaCache,
            () -> {
              throw new AssertionError("loader should not run when cache already has a value");
            }));
  }

  @Test
  void cachedValue_returnsAlreadyPublishedValueWhenAnotherLoadWinsTheRace() {
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals(
        "published-first",
        SqliteSchemaManager.cachedValue(
            schemaCache,
            () -> {
              schemaCache.set("published-first");
              return "loaded-late";
            }));
    assertEquals("published-first", schemaCache.get());
  }

  @Test
  void close_isIdempotent() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(tempDirectory.resolve("close-ok.sqlite"))) {
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_rejectsFurtherUse() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(tempDirectory.resolve("closed.sqlite"))) {
      postingFactStore.close();

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-closed")));

      assertEquals("SQLite book session is already closed.", exception.getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void close_wrapsNativeDatabaseCloseFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath);
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, postingFactStore::close);

    assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void commit_wrapsSchemaInitializationFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("schema-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath);
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                postingFactStore.commit(
                    postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

    assertTrue(exception.getMessage().contains("Failed to initialize SQLite book schema."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void commit_ignoresRollbackFailureWhenPrimaryFailureAlreadyExists() throws Exception {
    Path bookPath = tempDirectory.resolve("rollback-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath);
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
    setSchemaInitialized(postingFactStore, true);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                postingFactStore.commit(
                    postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

    assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
  }

  @Test
  void commit_primaryKeyConflictWithFirstReversalLeavesConstraintAsPrimaryFailure() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(databasePath)) {
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact(
                          "posting-1",
                          "idem-2",
                          Optional.of(new ReversalReference(new PostingId("posting-1"))),
                          Optional.of(new ReversalReason("full reversal")))));

      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void findByIdempotency_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("query-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath);
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-query")));

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  void findByIdempotency_wrapsJournalLineLoadFailureAfterPostingRowMatch()
      throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-partial")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void executeFindOnePosting_closesStatementWhenRowMappingFails() throws Exception {
    Path bookPath = tempDirectory.resolve("row-mapping-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
      SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
      setStoreDatabase(postingFactStore, database);

      Class<?> binderType =
          Class.forName("dev.erst.fingrind.sqlite.SqlitePostingFactStore$SqliteBinder");
      Method method =
          SqlitePostingFactStore.class.getDeclaredMethod(
              "executeFindOnePosting", SqliteNativeDatabase.class, String.class, binderType);
      method.setAccessible(true);
      ClassLoader proxyClassLoader =
          Optional.ofNullable(Thread.currentThread().getContextClassLoader())
              .orElseGet(SqlitePostingFactStoreTest.class::getClassLoader);
      Object binder =
          Proxy.newProxyInstance(
              proxyClassLoader,
              new Class<?>[] {binderType},
              (proxy, invokedMethod, arguments) -> null);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(postingFactStore, database, "select null as posting_id", binder));

      assertTrue(exception.getCause() instanceof NullPointerException);
    }
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

  private static int queryInt(SqliteNativeDatabase database, String sql)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
      int value = statement.columnInt(0);
      assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
      return value;
    }
  }

  private static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(reversalReference),
        reversalReference,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1")),
                reason),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalEntry journalEntry(Optional<ReversalReference> reversalReference) {
    if (reversalReference.isPresent()) {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
              line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
    }
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey)
      throws SqliteNativeException {
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
            '%s',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            'actor-1',
            'AGENT',
            'command-%s',
            '%s',
            'cause-1',
            null,
            null,
            'CLI',
            null
        )
        """
            .formatted(postingId, postingId, idempotencyKey));
  }

  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  private SqliteNativeDatabase staleDatabaseHandle(Path bookPath) throws Exception {
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
    MemorySegment handle = database.handle();
    database.close();
    return new SqliteNativeDatabase(handle);
  }

  private static void createPostingFactOnlyBook(Path bookPath) throws SqliteNativeException {
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
    try {
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
    } finally {
      database.close();
    }
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static void setStoreDatabase(
      SqlitePostingFactStore postingFactStore, SqliteNativeDatabase database) throws Exception {
    Field field = SqlitePostingFactStore.class.getDeclaredField("database");
    field.setAccessible(true);
    field.set(postingFactStore, database);
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static SqliteNativeDatabase storeDatabase(SqlitePostingFactStore postingFactStore)
      throws Exception {
    Field field = SqlitePostingFactStore.class.getDeclaredField("database");
    field.setAccessible(true);
    return (SqliteNativeDatabase) field.get(postingFactStore);
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static void setSchemaInitialized(
      SqlitePostingFactStore postingFactStore, boolean schemaInitialized) throws Exception {
    Field field = SqlitePostingFactStore.class.getDeclaredField("schemaInitialized");
    field.setAccessible(true);
    field.setBoolean(postingFactStore, schemaInitialized);
  }
}
