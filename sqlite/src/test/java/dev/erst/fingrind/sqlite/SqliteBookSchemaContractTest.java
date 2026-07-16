package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.LinkedHashSet;
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
  private static final ApplicationIdentity TEST_IDENTITY =
      new ApplicationIdentity("FinGrind", "0.56.0-test", "SQLite schema test identity");

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
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBookSchemaBootstrap.ensureParentDirectory(Path.of("/")));
    assertInvalidBookFilePathFailure(
        exception,
        Path.of("/"),
        "The FinGrind protected-book path does not satisfy the filesystem contract.");
  }

  @Test
  void openBook_setsFinGrindIdentityAndHardeningPragmas() throws Exception {
    Path databasePath = tempDirectory.resolve("identity-pragmas.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(
          Instant.parse("2026-04-07T10:15:30Z"),
          bookIdentity(),
          dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
              bookIdentity().bookDoctrine()));
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
    assertTrue(schema.contains("approved_at text not null check"));
    assertTrue(schema.contains("closed_at text not null check"));
    assertTrue(schema.contains("last_movement_date text not null check"));
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
  void schemaResource_postingTaxonomyAllowlistsMatchCanonicalOwnersAndPublishedRequestSurface()
      throws Exception {
    String schema = schemaResourceText();
    assertEquals(PostingKind.wireValues(), schemaAllowlist(schema, "posting_kind"));
    assertEquals(PostingOriginKind.wireValues(), schemaAllowlist(schema, "posting_origin_kind"));
    assertEquals(
        ForeignExchangeTreatmentKind.wireValues(), schemaAllowlist(schema, "treatment_kind"));
    assertEquals(PostingKind.wireValues(), publishedPostingKinds());
    assertEquals(PostingOriginKind.wireValues(), publishedPostingOriginKinds());
  }

  @Test
  void openBook_hardensBookDirectoryAndFilePermissionsOnSupportedHost() throws Exception {
    Path databasePath = tempDirectory.resolve("secure-book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(
          Instant.parse("2026-04-07T10:15:30Z"),
          bookIdentity(),
          dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
              bookIdentity().bookDoctrine()));
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
      initializeBookWithMinimalNumericAccounts(postingFactStore);
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
                      line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "1.00"))),
              false),
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
                  postingFactStore.openBook(
                      Instant.parse("2026-04-07T10:15:30Z"),
                      bookIdentity(),
                      dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                          bookIdentity().bookDoctrine())));
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

  private static String schemaResourceText() {
    try {
      return new String(
          java.util.Objects.requireNonNull(
                  SqliteBookSchemaBootstrap.class.getResourceAsStream("book_schema.sql"),
                  "Missing schema resource.")
              .readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read schema resource.", exception);
    }
  }

  private static List<String> schemaAllowlist(String schema, String columnName) {
    Pattern allowlistPattern =
        Pattern.compile(
            columnName
                + "\\s+text\\s+not\\s+null\\s+check\\s*\\(\\s*"
                + columnName
                + "\\s+in\\s*\\((.*?)\\)\\s*\\)",
            Pattern.DOTALL);
    Matcher allowlistMatcher = allowlistPattern.matcher(schema);
    assertTrue(allowlistMatcher.find(), "Missing allowlist check for " + columnName);
    Matcher literalMatcher = Pattern.compile("'([^']+)'").matcher(allowlistMatcher.group(1));
    List<String> values = new java.util.ArrayList<>();
    while (literalMatcher.find()) {
      values.add(literalMatcher.group(1));
    }
    return List.copyOf(values);
  }

  private static List<String> publishedPostingKinds() {
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        publishedPostEntryShape();
    Set<String> publishedValues = new LinkedHashSet<>();
    for (ContractRequestShapes.EntryKindSemanticsDescriptor semantics :
        postEntryShape.entryKindSemantics()) {
      publishedValues.add(postingKindFor(semantics.entryKind()).wireValue());
    }
    publishedValues.add(PostingKind.INTERIM_RESULT_SWEEP.wireValue());
    publishedValues.add(PostingKind.FISCAL_YEAR_CLOSE.wireValue());
    return PostingKind.wireValues().stream().filter(publishedValues::contains).toList();
  }

  private static List<String> publishedPostingOriginKinds() {
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        publishedPostEntryShape();
    Set<String> publishedValues = new LinkedHashSet<>();
    for (ContractRequestShapes.EntryKindSemanticsDescriptor semantics :
        postEntryShape.entryKindSemantics()) {
      publishedValues.add(
          PostingOriginKind.fromWireValue(semantics.entryKind().wireValue()).wireValue());
    }
    publishedValues.add(PostingOriginKind.INTERIM_RESULT_SWEEP.wireValue());
    publishedValues.add(PostingOriginKind.FISCAL_YEAR_CLOSE.wireValue());
    return PostingOriginKind.wireValues().stream().filter(publishedValues::contains).toList();
  }

  private static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
      publishedPostEntryShape() {
    return java.util.Objects.requireNonNull(
        MachineContract.capabilities(TEST_IDENTITY).requestShapes().bookkeepingEntry(),
        "Published machine contract must expose the bookkeeping-entry request shape.");
  }

  private static PostingKind postingKindFor(BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.OPENING_POSITION
        ? PostingKind.OPENING_BALANCE
        : PostingKind.STANDARD;
  }

  @Test
  void openBook_initializesCanonicalTablesAsStrict() {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())));
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
                  """
                  select strict
                  from pragma_table_list('inventory_movement')
                  where name = 'inventory_movement'
                  """));
          assertEquals(
              1,
              queryInt(
                  database,
                  """
                  select strict
                  from pragma_table_list('inventory_on_hand')
                  where name = 'inventory_on_hand'
                  """));
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
          postingFactStore.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())));
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
          postingFactStore.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())));
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
      postingFactStore.openBook(
          Instant.parse("2026-04-07T10:15:30Z"),
          bookIdentity(),
          dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
              bookIdentity().bookDoctrine()));
      assertEquals(1, queryInt(requireStoreDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals(
          "delete", queryText(requireStoreDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(requireStoreDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(requireStoreDatabase(postingFactStore), "pragma trusted_schema"));
    }
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
                SqliteBookContract.ACCOUNT_TABLE,
                SqliteBookContract.INVENTORY_MOVEMENT_TABLE,
                SqliteBookContract.INVENTORY_ON_HAND_TABLE,
                SqliteBookContract.POSTING_FACT_TABLE,
                SqliteBookContract.JOURNAL_LINE_TABLE,
                SqliteBookContract.INTERIM_RESULT_SWEEP_TABLE,
                SqliteBookContract.INTERIM_RESULT_SWEEP_TOTAL_TABLE,
                SqliteBookContract.INTERIM_RESULT_SWEEP_POSTING_TABLE,
                SqliteBookContract.FISCAL_YEAR_CLOSE_TABLE,
                SqliteBookContract.FISCAL_YEAR_CLOSE_POSTING_TABLE,
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
    Path missingInitializedMarkerPath =
        tempDirectory.resolve("fgrd-missing-initialized-marker.sqlite");
    createSchemaOnlyBook(missingInitializedMarkerPath);
    BookStateProbe missingInitializedMarkerProbe =
        withStandaloneDatabaseResult(
            bookAccess(missingInitializedMarkerPath),
            database ->
                new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString()));
    assertTrue(missingInitializedMarkerProbe.hasCanonicalTables());
    assertFalse(missingInitializedMarkerProbe.hasInitializedMarker());
    assertEquals("INCOMPLETE_FINGRIND", missingInitializedMarkerProbe.bookState());
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
}
