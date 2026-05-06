package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookSchemaContractTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void ensureParentDirectory_acceptsBareBookFileNames() {
    assertDoesNotThrow(
        () -> SqliteBookSchemaBootstrap.ensureParentDirectory(Path.of("book.sqlite")));
  }

  @Test
  void ensureParentDirectory_rejectsPathsWithoutWritableParentDirectory() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookSchemaBootstrap.ensureParentDirectory(Path.of("/")));
    assertEquals(
        "Book path must resolve against a writable parent directory.", exception.getMessage());
  }

  @Test
  void openBook_setsFinGrindIdentityAndHardeningPragmas() throws Exception {
    Path databasePath = tempDirectory.resolve("identity-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma secure_delete"));
      assertEquals(2, queryInt(storeDatabase(postingFactStore), "pragma temp_store"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              SqliteBookContract.APPLICATION_ID, queryInt(database, "pragma application_id"));
          assertEquals(
              SqliteBookContract.FORMAT_VERSION, queryInt(database, "pragma user_version"));
        });
  }

  @Test
  void schemaResource_pinsCommittedSourceChannelToCanonicalOwner() throws Exception {
    String schema =
        new String(
            java.util.Objects.requireNonNull(
                    SqliteBookSchemaBootstrap.class.getResourceAsStream("book_schema.sql"),
                    "Missing schema resource.")
                .readAllBytes(),
            StandardCharsets.UTF_8);

    assertTrue(
        schema.contains(
            "source_channel text not null check (source_channel in ('%s'))"
                .formatted(SourceChannel.CLI.wireValue())));
  }

  @Test
  void openBook_hardensBookDirectoryAndFilePermissionsOnSupportedHost() throws Exception {
    Path databasePath = tempDirectory.resolve("secure-book.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    }

    if (!databasePath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      return;
    }

    assertEquals(
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE),
        Files.getPosixFilePermissions(databasePath.getParent()));
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(databasePath));
  }

  @Test
  void encryptedBookFile_doesNotExposeObviousSentinelPlaintext() throws Exception {
    Path databasePath = tempDirectory.resolve("encrypted-sentinel.sqlite");
    String sentinelAccountName = "SENTINEL_ACCOUNT_NAME_X9Q2";
    String sentinelIdempotencyKey = "SENTINEL_IDEMPOTENCY_X9Q2";

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("9000"),
                  new AccountName(sentinelAccountName),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T12:00:00Z"))),
          postingFactStore.declareAccount(
              new AccountCode("9000"),
              new AccountName(sentinelAccountName),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T12:00:00Z")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact(
                  "posting-sentinel-1",
                  sentinelIdempotencyKey,
                  java.time.LocalDate.parse("2026-04-08"),
                  Instant.parse("2026-04-08T12:00:01Z"),
                  List.of(
                      line("9000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "1.00"),
                      line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "1.00")))),
          postingFactStore.commit(
              postingFact(
                  "posting-sentinel-1",
                  sentinelIdempotencyKey,
                  java.time.LocalDate.parse("2026-04-08"),
                  Instant.parse("2026-04-08T12:00:01Z"),
                  List.of(
                      line("9000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "1.00"),
                      line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "1.00")))));
    }

    String rawDatabaseBytes =
        new String(Files.readAllBytes(databasePath), StandardCharsets.ISO_8859_1);
    assertFalse(rawDatabaseBytes.contains(sentinelAccountName));
    assertFalse(rawDatabaseBytes.contains(sentinelIdempotencyKey));
    assertFalse(rawDatabaseBytes.contains("posting-sentinel-1"));
  }

  @Test
  void foreignAndUnsupportedBooks_areRejectedAcrossBoundaries() throws Exception {
    Path foreignBookPath = tempDirectory.resolve("foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", initializedException.getMessage());

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", accountException.getMessage());
    }

    Path unsupportedBookPath = tempDirectory.resolve("unsupported-version.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertTrue(initializedException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(openException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(accountException.getMessage().contains("format version 2 is unsupported"));
    }
  }

  @Test
  void openBook_initializesCanonicalTablesAsStrict() {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new BookOpeningOutcome.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('book_meta') where name = 'book_meta'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('account') where name = 'account'"));
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
        });
  }

  @Test
  void openBook_createsAccountCodeIndexForJournalLines() {
    Path databasePath = tempDirectory.resolve("journal-line-index.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new BookOpeningOutcome.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database ->
            assertEquals(
                1,
                queryInt(
                    database,
                    """
                    select count(*)
                    from pragma_index_list('journal_line')
                    where name = 'journal_line_by_account_code'
                    """)));
  }

  @Test
  void openBook_createsPostingHistoryIndexForReverseChronologicalPages() {
    Path databasePath = tempDirectory.resolve("posting-history-index.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new BookOpeningOutcome.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database ->
            assertEquals(
                1,
                queryInt(
                    database,
                    """
                    select count(*)
                    from pragma_index_list('posting_fact')
                    where name = 'posting_fact_by_effective_recorded_posting'
                    """)));
  }

  @Test
  void openBook_configuresOpenConnectionForHardeningAndDurability() throws Exception {
    Path databasePath = tempDirectory.resolve("connection-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
    }
  }

  @Test
  void canonicalStrictSchema_rejectsNonLosslessTypeMismatches() {
    Path bookPath = tempDirectory.resolve("strict-datatype.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertInitializedAtRow(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
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

                  assertEquals(SqliteNativeResultCodes.CONSTRAINT_DATATYPE, exception.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_DATATYPE", exception.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsPersistedIdentifierValuesOutsideTheDomainContract() {
    Path bookPath = tempDirectory.resolve("identifier-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertInitializedAtRow(database);

                  SqliteNativeException invalidAccountCode =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "_1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidAccountCode.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountCode.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));

                  SqliteNativeException invalidAccountName =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "1000", "   ", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidAccountName.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountName.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));

                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");

                  SqliteNativeException invalidIdempotencyKey =
                      assertThrows(
                          SqliteNativeException.class,
                          () -> insertPostingFactRow(database, "posting-1", "idem key"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidIdempotencyKey.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidIdempotencyKey.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));

                  SqliteNativeException invalidActorId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-actor",
                                  "   ",
                                  "command-actor",
                                  "idem-actor",
                                  "cause-actor",
                                  "null"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidActorId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidActorId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));

                  SqliteNativeException invalidCommandId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-command",
                                  "actor-command",
                                  "   ",
                                  "idem-command",
                                  "cause-command",
                                  "null"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidCommandId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCommandId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));

                  SqliteNativeException invalidCausationId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-cause",
                                  "actor-cause",
                                  "command-cause",
                                  "idem-cause",
                                  "   ",
                                  "null"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidCausationId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCausationId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));

                  SqliteNativeException invalidCorrelationId =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-correlation",
                                  "actor-correlation",
                                  "command-correlation",
                                  "idem-correlation",
                                  "cause-correlation",
                                  "'   '"));
                  assertEquals(
                      SqliteNativeResultCodes.CONSTRAINT_CHECK, invalidCorrelationId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCorrelationId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                }));
  }

  @Test
  void loadInitializedAt_returnsEmptyWithoutMarkerAndValueWhenPresent() throws Exception {
    Path missingMarkerPath = tempDirectory.resolve("initialized-at-missing.sqlite");
    createSchemaOnlyBook(missingMarkerPath);
    Optional<Instant> missingInitializedAt =
        withStandaloneDatabaseResult(
            bookAccess(missingMarkerPath), SqliteStatementQueries::loadInitializedAt);
    assertEquals(Optional.empty(), missingInitializedAt);

    Path presentMarkerPath = tempDirectory.resolve("initialized-at-present.sqlite");
    initializeBookOnDisk(presentMarkerPath);
    Optional<Instant> presentInitializedAt =
        withStandaloneDatabaseResult(
            bookAccess(presentMarkerPath), SqliteStatementQueries::loadInitializedAt);
    assertEquals(Optional.of(Instant.parse("2026-04-07T10:15:30Z")), presentInitializedAt);
  }

  @Test
  void bookPath_and_passphraseDelegates_matchTheSharedStoreContext() {
    Path bookPath = tempDirectory.resolve("delegate-path.sqlite");
    BookAccess access = bookAccess(bookPath);

    try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(access)) {
      assertEquals(bookPath, postingFactStore.bookPath());
      try (SqliteBookPassphrase delegated =
              SqlitePostingFactStore.passphraseDecisionFor(access).requireAccepted();
          SqliteBookPassphrase forwarded =
              SqlitePostingFactStore.passphraseFor(access).requireAccepted()) {
        assertEquals(delegated.sourceDescription(), forwarded.sourceDescription());
        assertArrayEquals(delegated.utf8BytesCopy(), forwarded.utf8BytesCopy());
      }
    }
  }

  @Test
  void assertOpenConfiguration_rejectsHardeningDrift() throws Exception {
    List<OpenConfigurationDrift> driftCases =
        List.of(
            new OpenConfigurationDrift(
                "pragma foreign_keys = off",
                "SQLite connection failed to keep foreign_keys enabled."),
            new OpenConfigurationDrift(
                "pragma journal_mode = wal",
                "SQLite connection failed to enforce journal_mode=DELETE."),
            new OpenConfigurationDrift(
                "pragma synchronous = normal",
                "SQLite connection failed to enforce synchronous=EXTRA."),
            new OpenConfigurationDrift(
                "pragma trusted_schema = on",
                "SQLite connection failed to disable trusted_schema."),
            new OpenConfigurationDrift(
                "pragma secure_delete = off", "SQLite connection failed to enable secure_delete."),
            new OpenConfigurationDrift(
                "pragma temp_store = file", "SQLite connection failed to force temp_store=MEMORY."),
            new OpenConfigurationDrift(
                "pragma memory_security = off",
                "SQLite connection failed to enable memory_security=fill."),
            new OpenConfigurationDrift(
                "pragma query_only = on",
                "SQLite connection failed to enforce the expected query_only setting."));

    for (OpenConfigurationDrift driftCase : driftCases) {
      assertOpenConfigurationFailure(driftCase.pragma(), driftCase.failureMessage());
    }
  }

  @Test
  void requirePragmaValue_rejectsUnexpectedValues() {
    assertDoesNotThrow(
        () -> SqliteConnectionConfigurer.requirePragmaValue(1, 1, "should accept expected value"));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteConnectionConfigurer.requirePragmaValue(
                    0, 1, "SQLite connection failed to enable memory_security=fill."));

    assertEquals(
        "SQLite connection failed to enable memory_security=fill.", exception.getMessage());
  }

  @Test
  void bookStateHelpers_coverCanonicalAndMarkerShortCircuits() throws Exception {
    SqliteBookStateReader bookStateReader =
        new SqliteBookStateReader(
            SqliteBookContract.APPLICATION_ID,
            SqliteBookContract.FORMAT_VERSION,
            "account",
            "book_meta",
            "journal_line",
            "posting_fact");

    Path noMetaPath = tempDirectory.resolve("fgrd-no-meta.sqlite");
    createPartialFinGrindBook(noMetaPath, false, false, false, false, false);
    BookStateProbe noMetaProbe =
        withStandaloneDatabaseResult(
            bookAccess(noMetaPath),
            database ->
                new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString()));
    assertFalse(noMetaProbe.hasCanonicalTables());
    assertFalse(noMetaProbe.hasInitializedMarker());
    assertEquals("INCOMPLETE_FINGRIND", noMetaProbe.bookState());

    Path noAccountPath = tempDirectory.resolve("fgrd-no-account.sqlite");
    createPartialFinGrindBook(noAccountPath, true, false, false, false, false);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noAccountPath), bookStateReader::hasCanonicalTables));

    Path noPostingPath = tempDirectory.resolve("fgrd-no-posting.sqlite");
    createPartialFinGrindBook(noPostingPath, true, true, false, false, false);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noPostingPath), bookStateReader::hasCanonicalTables));

    Path noJournalLinePath = tempDirectory.resolve("fgrd-no-journal-line.sqlite");
    createPartialFinGrindBook(noJournalLinePath, true, true, true, false, false);
    BookStateProbe noJournalLineProbe =
        withStandaloneDatabaseResult(
            bookAccess(noJournalLinePath),
            database ->
                new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString()));
    assertFalse(noJournalLineProbe.hasCanonicalTables());
    assertEquals("INCOMPLETE_FINGRIND", noJournalLineProbe.bookState());

    Path initializedPath = tempDirectory.resolve("fgrd-initialized-short-circuit.sqlite");
    initializeBookOnDisk(initializedPath);
    BookStateProbe initializedProbe =
        withStandaloneDatabaseResult(
            bookAccess(initializedPath),
            database -> {
              try (SqlitePostingFactStore postingFactStore =
                  new SqlitePostingFactStore(bookAccess(initializedPath))) {
                assertDoesNotThrow(() -> postingFactStore.requireInitializedBook(database));
                return new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString());
              }
            });
    assertTrue(initializedProbe.hasCanonicalTables());
    assertTrue(initializedProbe.hasInitializedMarker());
    assertEquals("INITIALIZED_FINGRIND", initializedProbe.bookState());

    Path versionOnlyPath = tempDirectory.resolve("foreign-version-only.sqlite");
    withStandaloneDatabase(
        bookAccess(versionOnlyPath),
        database -> database.executeStatement("pragma user_version = 1"));
    String versionOnlyBookState =
        withStandaloneDatabaseResult(
            bookAccess(versionOnlyPath),
            database -> bookStateReader.bookState(database).toString());
    assertEquals("FOREIGN_SQLITE", versionOnlyBookState);
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String actorId,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationIdSqlLiteral) {
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
            '%s',
            'AGENT',
            '%s',
            '%s',
            '%s',
            %s,
            null,
            '%s',
            null
        )
        """
            .formatted(
                postingId,
                actorId,
                commandId,
                idempotencyKey,
                causationId,
                correlationIdSqlLiteral,
                SourceChannel.CLI.wireValue()));
  }
}
