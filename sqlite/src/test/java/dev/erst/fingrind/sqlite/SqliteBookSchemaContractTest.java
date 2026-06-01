package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookSchemaContractTest extends SqlitePostingFactStoreTestSupport {
  private static final Pattern APPLICATION_ID_PRAGMA =
      Pattern.compile("^pragma\\s+application_id\\s*=\\s*(\\d+)\\s*;\\s*$", Pattern.MULTILINE);
  private static final Pattern USER_VERSION_PRAGMA =
      Pattern.compile("^pragma\\s+user_version\\s*=\\s*(\\d+)\\s*;\\s*$", Pattern.MULTILINE);

  @Test
  void ensureParentDirectory_acceptsBareBookFileNames() {
    AtomicReference<Path> ensuredPath = new AtomicReference<>();
    Path bareBookPath = Path.of("book.sqlite");
    assertDoesNotThrow(
        () ->
            SqliteBookSchemaBootstrap.ensureParentDirectory(
                bareBookPath, normalizedPath -> ensuredPath.set(normalizedPath)));
    assertEquals(bareBookPath.toAbsolutePath().normalize(), ensuredPath.get());
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
      assertEquals(1, queryInt(requireStoreDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals(
          "delete", queryText(requireStoreDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(requireStoreDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(requireStoreDatabase(postingFactStore), "pragma trusted_schema"));
      assertEquals(1, queryInt(requireStoreDatabase(postingFactStore), "pragma secure_delete"));
      assertEquals(2, queryInt(requireStoreDatabase(postingFactStore), "pragma temp_store"));
      assertEquals(0, queryInt(requireStoreDatabase(postingFactStore), "pragma query_only"));
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
  void schemaResource_enforcesCanonicalTemporalTextAndClosedSourceChannelVocabulary()
      throws Exception {
    String schema =
        new String(
            java.util.Objects.requireNonNull(
                    SqliteBookSchemaBootstrap.class.getResourceAsStream("book_schema.sql"),
                    "Missing schema resource.")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertTrue(
        schema.contains(
            "source_channel text not null check (source_channel in ('CLI', 'SYSTEM'))"));
    assertTrue(schema.contains("declared_at text not null check"));
    assertTrue(schema.contains("effective_date text not null check"));
    assertTrue(schema.contains("recorded_at text not null check"));
    assertTrue(schema.contains("document_date text not null check"));
    assertTrue(schema.contains("captured_at text not null check"));
    assertTrue(schema.contains("approved_at text not null check"));
    assertTrue(schema.contains("closed_at text not null check"));
    assertTrue(schema.contains("meta_key = 'initialized_at'"));
    assertTrue(schema.contains("meta_key = 'schema_fingerprint_sha256'"));
    assertTrue(schema.contains("length(value) = 64"));
  }

  @Test
  void schemaResource_pragmasMatchCanonicalBookFormatContract() throws Exception {
    String schema =
        new String(
            java.util.Objects.requireNonNull(
                    SqliteBookSchemaBootstrap.class.getResourceAsStream("book_schema.sql"),
                    "Missing schema resource.")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    Matcher applicationIdMatcher = APPLICATION_ID_PRAGMA.matcher(schema);
    Matcher userVersionMatcher = USER_VERSION_PRAGMA.matcher(schema);
    assertTrue(
        applicationIdMatcher.find(), "book_schema.sql must declare one application_id pragma.");
    assertTrue(userVersionMatcher.find(), "book_schema.sql must declare one user_version pragma.");
    assertEquals(
        SqliteBookContract.APPLICATION_ID, Integer.parseInt(applicationIdMatcher.group(1)));
    assertEquals(SqliteBookContract.FORMAT_VERSION, Integer.parseInt(userVersionMatcher.group(1)));
  }

  @Test
  void openBook_hardensBookDirectoryAndFilePermissionsOnSupportedHost() throws Exception {
    Path databasePath = tempDirectory.resolve("secure-book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("9000"),
                  new AccountName(sentinelAccountName),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T12:00:00Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("9000"),
              new AccountName(sentinelAccountName),
              dev.erst.fingrind.core.AccountType.ASSET,
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
          commitPosting(
              postingFactStore,
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(foreignBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.FOREIGN_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", accountException.getMessage());
    }
    Path unsupportedBookPath = tempDirectory.resolve("unsupported-version.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    int unsupportedVersion = SqliteBookContract.FORMAT_VERSION + 1;
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = " + unsupportedVersion));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(unsupportedBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              SqliteBookContract.APPLICATION_ID,
              unsupportedVersion,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(
          NullTestSupport.messageOf(openException)
              .contains("format version " + unsupportedVersion + " is unsupported"));
      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(
          NullTestSupport.messageOf(accountException)
              .contains("format version " + unsupportedVersion + " is unsupported"));
    }
  }

  @Test
  void openBook_initializesCanonicalTablesAsStrict() {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(postingFactStore.inspectBook().initialized());
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
      assertEquals(1, queryInt(requireStoreDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals(
          "delete", queryText(requireStoreDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(requireStoreDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(requireStoreDatabase(postingFactStore), "pragma trusted_schema"));
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
                  insertCanonicalInitializedBookMetadata(database);
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
                                      amount_minor
                                  ) values (
                                      'posting-1',
                                      'not-an-integer',
                                      '1000',
                                      'DEBIT',
                                      'EUR',
                                      1000
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_DATATYPE"), exception.resultCode());
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
                  insertCanonicalInitializedBookMetadata(database);
                  SqliteNativeException invalidAccountCode =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "_1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidAccountCode.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountCode.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));
                  SqliteNativeException invalidAccountName =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertAccountRow(
                                  database, "1000", "   ", "DEBIT", 1, "2026-04-07T10:15:30Z"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidAccountName.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAccountName.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from account"));
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  SqliteNativeException invalidIdempotencyKey =
                      assertThrows(
                          SqliteNativeException.class,
                          () -> insertPostingFactRow(database, "posting-1", "idem key"));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidIdempotencyKey.resultCode());
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
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"), invalidActorId.resultCode());
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
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCommandId.resultCode());
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
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCausationId.resultCode());
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
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
                      invalidCorrelationId.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidCorrelationId.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from posting_fact"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsImpossibleFiscalYearAnchors() {
    Path bookPath = tempDirectory.resolve("invalid-fiscal-year-anchor.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  SqliteNativeException invalidAnchor =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into book_identity (
                                      singleton_id,
                                      entity_name,
                                      accounting_kernel_profile,
                                      functional_currency_code,
                                      fiscal_year_start_month,
                                      fiscal_year_start_day
                                  ) values (
                                      1,
                                      'Acme Studio',
                                      'country-agnostic-bookkeeping-kernel',
                                      'EUR',
                                      2,
                                      30
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_CHECK"), invalidAnchor.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_CHECK", invalidAnchor.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from book_identity"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsJournalLineCurrencyOutsideBookFunctionalCurrency() {
    Path bookPath = tempDirectory.resolve("journal-line-functional-currency.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-1", "idem-1");

                  SqliteNativeException mismatchedCurrency =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database, "posting-1", 0, "1000", "DEBIT", "USD", 1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      mismatchedCurrency.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", mismatchedCurrency.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsNonHeaderAndTaxonomyMismatchedParents() {
    Path bookPath = tempDirectory.resolve("account-parent-contract.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");

                  SqliteNativeException nonHeaderParent =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
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
                                      '1010',
                                      'Petty Cash',
                                      'ASSET',
                                      'ORDINARY',
                                      'POSTABLE',
                                      '1000',
                                      'CURRENT_ASSET',
                                      null,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      nonHeaderParent.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", nonHeaderParent.resultName());

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
                          '1100',
                          'Cash Header',
                          'ASSET',
                          'ORDINARY',
                          'HEADER',
                          null,
                          'CURRENT_ASSET',
                          null,
                          1,
                          '2026-04-07T10:15:30Z'
                      )
                      """);

                  SqliteNativeException taxonomyMismatch =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
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
                                      '1110',
                                      'Equipment',
                                      'ASSET',
                                      'ORDINARY',
                                      'POSTABLE',
                                      '1100',
                                      'NONCURRENT_ASSET',
                                      null,
                                      1,
                                      '2026-04-07T10:15:30Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      taxonomyMismatch.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", taxonomyMismatch.resultName());
                  assertEquals(2, queryInt(database, "select count(*) from account"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsLateOpeningBalanceInsertions() {
    Path bookPath = tempDirectory.resolve("late-opening-balance.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "3000",
                      "Opening Equity",
                      "EQUITY",
                      "CREDIT",
                      1,
                      "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-standard",
                      "STANDARD",
                      "2026-04-07",
                      "2026-04-07T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-standard",
                          "AGENT",
                          "command-standard",
                          "idem-standard",
                          "cause-standard",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-standard", 0, "1000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-standard", 1, "3000", "CREDIT", "EUR", 1000);

                  SqliteNativeException lateOpeningBalance =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-opening-balance",
                                  "OPENING_BALANCE",
                                  "2026-04-01",
                                  "2026-04-07T10:15:31Z",
                                  new PostingFactSqlLiterals(
                                      "actor-opening",
                                      "AGENT",
                                      "command-opening",
                                      "idem-opening",
                                      "cause-opening",
                                      "null",
                                      "null",
                                      SourceChannel.CLI.wireValue(),
                                      "null")));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      lateOpeningBalance.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", lateOpeningBalance.resultName());
                  assertEquals(1, queryInt(database, "select count(*) from posting_fact"));
                }));
  }

  @Test
  void canonicalStrictSchema_rejectsInactiveAccountAndOpeningBalanceNominalUsage() {
    Path inactiveAccountPath = tempDirectory.resolve("inactive-account-journal-line.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(inactiveAccountPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "1100",
                      "Dormant Cash",
                      "ASSET",
                      "DEBIT",
                      0,
                      "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-inactive", "idem-inactive");

                  SqliteNativeException inactiveAccountLine =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database, "posting-inactive", 0, "1100", "DEBIT", "EUR", 1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      inactiveAccountLine.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", inactiveAccountLine.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));

    Path nominalOpeningBalancePath = tempDirectory.resolve("opening-balance-nominal.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(nominalOpeningBalancePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-opening-balance",
                      "OPENING_BALANCE",
                      "2026-04-01",
                      "2026-04-07T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-opening",
                          "AGENT",
                          "command-opening",
                          "idem-opening",
                          "cause-opening",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));

                  SqliteNativeException nominalOpeningBalanceLine =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertJournalLineRow(
                                  database,
                                  "posting-opening-balance",
                                  0,
                                  "4000",
                                  "CREDIT",
                                  "EUR",
                                  1000));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      nominalOpeningBalanceLine.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", nominalOpeningBalanceLine.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void
      canonicalStrictSchema_rejectsTransferredPeriodResultBackfillAndBrokenPeriodResultTransferLinks() {
    Path invalidCloseTargetPath =
        tempDirectory.resolve("invalid-period-result-transfer-target.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(invalidCloseTargetPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");

                  SqliteNativeException invalidCloseTarget =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into period_result_transfer (
                                      period_result_transfer_order,
                                      effective_date_from,
                                      effective_date_to,
                                      closing_equity_account_code,
                                      closed_at
                                  ) values (
                                      1,
                                      '2026-04-01',
                                      '2026-04-30',
                                      '4000',
                                      '2026-04-30T23:59:59Z'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      invalidCloseTarget.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", invalidCloseTarget.resultName());
                }));

    Path transferredPeriodResultPath = tempDirectory.resolve("closed-period-backfill.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(transferredPeriodResultPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertCanonicalInitializedBookMetadata(database);
                  insertAccountRow(
                      database, "1000", "Cash", "ASSET", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database,
                      "3000",
                      "Retained Earnings",
                      "EQUITY",
                      "CREDIT",
                      1,
                      "2026-04-07T10:15:30Z");
                  insertAccountRow(
                      database, "4000", "Sales", "REVENUE", "CREDIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(
                      database,
                      "posting-period-result-transfer",
                      "PERIOD_RESULT_TRANSFER",
                      "2026-04-30",
                      "2026-04-30T23:59:59Z",
                      new PostingFactSqlLiterals(
                          "system:periodResultTransfer",
                          "SYSTEM",
                          "periodResultTransfer:2026-04",
                          "periodResultTransfer:2026-04",
                          "periodResultTransfer:2026-04",
                          "'periodResultTransfer:2026-04'",
                          "null",
                          SourceChannel.SYSTEM.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-period-result-transfer", 0, "4000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-period-result-transfer", 1, "3000", "CREDIT", "EUR", 1000);
                  database.executeStatement(
                      """
                      insert into period_result_transfer (
                          period_result_transfer_order,
                          effective_date_from,
                          effective_date_to,
                          closing_equity_account_code,
                          closed_at
                      ) values (
                          1,
                          '2026-04-01',
                          '2026-04-30',
                          '3000',
                          '2026-04-30T23:59:59Z'
                      )
                      """);
                  database.executeStatement(
                      """
                      insert into period_result_transfer_posting (
                          period_result_transfer_order,
                          posting_id
                      ) values (
                          1,
                          'posting-period-result-transfer'
                      )
                      """);
                  insertPostingFactRow(
                      database,
                      "posting-post-close",
                      "STANDARD",
                      "2026-05-01",
                      "2026-05-01T10:15:30Z",
                      new PostingFactSqlLiterals(
                          "actor-post-close",
                          "AGENT",
                          "command-post-close",
                          "idem-post-close",
                          "cause-post-close",
                          "null",
                          "null",
                          SourceChannel.CLI.wireValue(),
                          "null"));
                  insertJournalLineRow(
                      database, "posting-post-close", 0, "1000", "DEBIT", "EUR", 1000);
                  insertJournalLineRow(
                      database, "posting-post-close", 1, "3000", "CREDIT", "EUR", 1000);

                  SqliteNativeException backfilledPosting =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              insertPostingFactRow(
                                  database,
                                  "posting-closed-period",
                                  "STANDARD",
                                  "2026-04-15",
                                  "2026-05-01T10:15:31Z",
                                  new PostingFactSqlLiterals(
                                      "actor-closed-period",
                                      "AGENT",
                                      "command-closed-period",
                                      "idem-closed-period",
                                      "cause-closed-period",
                                      "null",
                                      "null",
                                      SourceChannel.CLI.wireValue(),
                                      "null")));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      backfilledPosting.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", backfilledPosting.resultName());

                  SqliteNativeException brokenCloseLink =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into period_result_transfer_posting (
                                      period_result_transfer_order,
                                      posting_id
                                  ) values (
                                      1,
                                      'posting-post-close'
                                  )
                                  """));
                  assertEquals(
                      SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
                      brokenCloseLink.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_TRIGGER", brokenCloseLink.resultName());
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
  void lifecycleInspectionMapper_rejectsInitializedBooksMissingInitializedAtMetadata()
      throws Exception {
    Path initializedBookPath = tempDirectory.resolve("initialized-at-required.sqlite");
    initializeBookOnDisk(initializedBookPath);

    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          database.executeStatement("delete from book_meta where meta_key = 'initialized_at'");

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteBookLifecycleInspectionMapper.fromSnapshot(
                          new SqliteBookStateSnapshot(
                              SqliteBookContract.APPLICATION_ID,
                              SqliteBookContract.FORMAT_VERSION,
                              SqliteBookState.INITIALIZED_FINGRIND),
                          database));

          assertEquals(
              "Initialized SQLite book is missing initialized-at metadata.",
              exception.getMessage());
        });
  }

  @Test
  void bookPath_and_passphraseDelegates_matchTheSharedStoreContext() {
    Path bookPath = tempDirectory.resolve("delegate-path.sqlite");
    BookAccess access = bookAccess(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(access)) {
      assertEquals(bookPath, postingFactStore.bookPath());
      try (SqliteBookPassphrase delegated = loadPassphrase(access);
          SqliteBookPassphrase reopened = loadPassphrase(access)) {
        assertEquals(delegated.sourceDescription(), reopened.sourceDescription());
        assertArrayEquals(delegated.utf8BytesCopy(), reopened.utf8BytesCopy());
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
            java.util.List.of(
                SqliteBookContract.BOOK_META_TABLE,
                SqliteBookContract.BOOK_IDENTITY_TABLE,
                SqliteBookContract.ENTITY_PROFILE_TABLE,
                SqliteBookContract.ACCOUNT_TABLE,
                SqliteBookContract.POSTING_FACT_TABLE,
                SqliteBookContract.JOURNAL_LINE_TABLE,
                SqliteBookContract.PERIOD_RESULT_TRANSFER_TABLE,
                SqliteBookContract.PERIOD_RESULT_TRANSFER_TOTAL_TABLE,
                SqliteBookContract.PERIOD_RESULT_TRANSFER_POSTING_TABLE,
                SqliteBookContract.AUDIT_EVENT_TABLE));
    Path noMetaPath = tempDirectory.resolve("fgrd-no-meta.sqlite");
    createPartialFinGrindBook(noMetaPath, false, SqliteBookContract.BOOK_META_TABLE);
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
    createPartialFinGrindBook(noAccountPath, true, SqliteBookContract.ACCOUNT_TABLE);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noAccountPath), bookStateReader::hasCanonicalTables));
    Path noAuditEventPath = tempDirectory.resolve("fgrd-no-audit-event.sqlite");
    createPartialFinGrindBook(noAuditEventPath, true, SqliteBookContract.AUDIT_EVENT_TABLE);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noAuditEventPath), bookStateReader::hasCanonicalTables));
    Path noPostingPath = tempDirectory.resolve("fgrd-no-posting.sqlite");
    createPartialFinGrindBook(noPostingPath, true, SqliteBookContract.POSTING_FACT_TABLE);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noPostingPath), bookStateReader::hasCanonicalTables));
    Path noJournalLinePath = tempDirectory.resolve("fgrd-no-journal-line.sqlite");
    createPartialFinGrindBook(noJournalLinePath, true, SqliteBookContract.JOURNAL_LINE_TABLE);
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
                  openStore(bookAccess(initializedPath))) {
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
    insertPostingFactRow(
        database,
        postingId,
        "STANDARD",
        "2026-04-07",
        "2026-04-07T10:15:30Z",
        new PostingFactSqlLiterals(
            actorId,
            "AGENT",
            commandId,
            idempotencyKey,
            causationId,
            correlationIdSqlLiteral,
            "null",
            SourceChannel.CLI.wireValue(),
            "null"));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String postingKind,
      String effectiveDate,
      String recordedAt,
      PostingFactSqlLiterals sqlLiterals) {
    String postingOriginKind = defaultPostingOriginKind(postingKind, sqlLiterals);
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            posting_origin_kind,
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
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            '%s',
            %s,
            %s,
            '%s',
            %s
        )
        """
            .formatted(
                postingId,
                postingKind,
                postingOriginKind,
                effectiveDate,
                recordedAt,
                sqlLiterals.actorId(),
                sqlLiterals.actorType(),
                sqlLiterals.commandId(),
                sqlLiterals.idempotencyKey(),
                sqlLiterals.causationId(),
                sqlLiterals.correlationIdSqlLiteral(),
                sqlLiterals.reasonSqlLiteral(),
                sqlLiterals.sourceChannel(),
                sqlLiterals.priorPostingIdSqlLiteral()));
  }

  private static String defaultPostingOriginKind(
      String postingKind, PostingFactSqlLiterals sqlLiterals) {
    return switch (postingKind) {
      case "OPENING_BALANCE" ->
          dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT.wireValue();
      case "PERIOD_RESULT_TRANSFER" ->
          dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER.wireValue();
      default ->
          isReversal(sqlLiterals)
              ? dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT.wireValue()
              : dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT.wireValue();
    };
  }

  private static boolean isReversal(PostingFactSqlLiterals sqlLiterals) {
    return !"null".equals(sqlLiterals.reasonSqlLiteral())
        || !"null".equals(sqlLiterals.priorPostingIdSqlLiteral());
  }

  private record PostingFactSqlLiterals(
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      String correlationIdSqlLiteral,
      String reasonSqlLiteral,
      String sourceChannel,
      String priorPostingIdSqlLiteral) {}
}
