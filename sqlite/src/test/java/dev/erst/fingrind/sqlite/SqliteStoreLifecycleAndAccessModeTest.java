package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteStoreLifecycleAndAccessModeTest extends SqlitePostingFactStoreTestSupport {
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE_POLICY =
      PostingAcceptancePolicy.currentKernel();
  private static final Class<?> SESSION_STATE_CLASS = lifecycleNestedType("SessionState");
  private static final Class<?> IDLE_SESSION_CLASS = lifecycleNestedType("IdleSession");
  private static final Class<?> OPENED_SESSION_CLASS = lifecycleNestedType("OpenedSession");
  private static final Class<?> FAILED_SESSION_CLASS = lifecycleNestedType("FailedSession");
  private static final Class<?> CLOSED_SESSION_CLASS = lifecycleNestedType("ClosedSession");
  private static final MethodHandles.Lookup LIFECYCLE_LOOKUP = lifecycleLookup();
  private static final VarHandle SESSION_STATE_HANDLE = lifecycleSessionStateHandle();
  private static final MethodHandle CACHED_BOOK_STATE_HANDLE =
      lifecycleMethodHandle(
          "cachedBookState", MethodType.methodType(SqliteBookStateSnapshot.class));
  private static final MethodHandle DETACH_PUBLISHED_DATABASE_HANDLE =
      lifecycleMethodHandle(
          "detachPublishedDatabase", MethodType.methodType(SqliteNativeDatabase.class));
  private static final MethodHandle REMEMBER_TERMINAL_FAILURE_HANDLE =
      lifecycleMethodHandle(
          "rememberTerminalFailure",
          MethodType.methodType(IllegalStateException.class, IllegalStateException.class));
  private static final MethodHandle REMEMBERED_REJECTED_FAILURE_HANDLE =
      lifecycleMethodHandle(
          "rememberedRejectedFailure",
          MethodType.methodType(
              ContractFailureException.class,
              dev.erst.fingrind.contract.runtime.ContractFailure.class));

  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteBookSchemaBootstrap.readSchema(this::failingInputStream));
  }

  @Test
  void initializeBook_executesWholeSchemaScriptWithoutStatementSplitting() {
    Path bookPath = tempDirectory.resolve("schema-script.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(
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
                      SqliteNativeStatements.prepare(database, "select note from sample_audit")) {
                    assertEquals(SqliteNativeResultCodes.ROW, statement.step());
                    assertEquals("semi;colon", statement.columnText(0));
                    assertEquals(SqliteNativeResultCodes.DONE, statement.step());
                  }
                }));
  }

  @Test
  void cachedValue_loadsAndStoresValueWhenCacheIsEmpty() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>();
    assertEquals("loaded", SqliteBookSchemaBootstrap.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>("cached");
    assertEquals(
        "cached",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              throw new AssertionError("loader should not run when cache already has a value");
            }));
  }

  @Test
  void cachedValue_returnsAlreadyPublishedValueWhenAnotherLoadWinsTheRace() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>();
    assertEquals(
        "published-first",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              schemaCache.set("published-first");
              return "loaded-late";
            }));
    assertEquals("published-first", schemaCache.get());
  }

  @Test
  void ensureParentDirectory_wrapsDirectoryPreparationFailures() {
    Path bookPath = tempDirectory.resolve("wrapped-parent").resolve("book.sqlite");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookSchemaBootstrap.ensureParentDirectory(
                    bookPath,
                    normalizedBookPath -> {
                      throw new IOException("boom");
                    }));

    assertEquals("Failed to create SQLite book directory.", exception.getMessage());
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void close_isIdempotent() {
    try (SqlitePostingFactStore postingFactStore =
        openStore(bookAccess(tempDirectory.resolve("close-ok.sqlite")))) {
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_afterDatabaseOpenRemainsIdempotent() throws Exception {
    Path bookPath = tempDirectory.resolve("close-opened.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      assertDoesNotThrow(() -> postingFactStore.listAccounts(firstAccountPage()));
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_zeroizesPendingPassphraseWhenDatabaseWasNeverOpened() throws Exception {
    SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters(
            "test close pending passphrase", TEST_BOOK_KEY.toCharArray());
    byte[] expectedZeroes = new byte[TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length];
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(tempDirectory.resolve("never-opened.sqlite"), passphrase)) {
      postingFactStore.close();
    }
    assertArrayEquals(expectedZeroes, passphraseBytes(passphrase));
  }

  @Test
  void storeRetainsStableOpenFailureAfterPassphraseConsumption() throws Exception {
    Path invalidBookPath = tempDirectory.resolve("invalid-retry.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      IllegalStateException secondFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertProtectedBookVerificationFailure(firstFailure);
      assertSame(firstFailure, secondFailure);
    }
  }

  @Test
  void protectedBookVerificationFailure_coversCorruptedAndTruncatedProtectedBooks()
      throws Exception {
    Path intactBookPath = tempDirectory.resolve("intact-protected.sqlite");
    initializeBookOnDisk(intactBookPath);
    byte[] intactBytes = Files.readAllBytes(intactBookPath);
    Path corruptedBookPath = tempDirectory.resolve("corrupted-protected.sqlite");
    byte[] corruptedBytes = intactBytes.clone();
    corruptedBytes[Math.min(200, corruptedBytes.length - 1)] ^= 0x5A;
    Files.write(corruptedBookPath, corruptedBytes);
    Path truncatedBookPath = tempDirectory.resolve("truncated-protected.sqlite");
    Files.write(truncatedBookPath, Arrays.copyOf(intactBytes, 128));
    assertProtectedBookVerificationFailure(corruptedBookPath);
    assertProtectedBookVerificationFailure(truncatedBookPath);
  }

  @Test
  void protectedBookVerificationFailure_coversAllCanonicalVerificationResultCodes() {
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(SqliteNativeResultCodes.NOTADB, "not a database"))
            .isPresent());
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(
                    SqliteNativeResultCodes.IOERR_BADKEY, "cipher verification failed"))
            .isPresent());
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(
                    SqliteNativeResultCodes.IOERR_CODEC, "codec verification failed"))
            .isPresent());
    assertEquals(
        Optional.empty(),
        SqliteStoreOperations.protectedBookVerificationFailure(
            new SqliteNativeException(SqliteNativeResultCodes.ERROR, "ordinary runtime failure")));
  }

  @Test
  void lifecycleOpen_wrapsNonVerificationNativeOpenFailures() {
    Path bookPath = tempDirectory.resolve("open-runtime-failure.sqlite");
    SqliteStoreContext context =
        new SqliteStoreContext(
            bookPath, SqliteStoreAccessMode.READ_ONLY, SqliteNativeBootstrap::api) {
          @Override
          SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
            throw new SqliteNativeException(SqliteNativeResultCodes.ERROR, "open-boom");
          }
        };
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "open runtime failure", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle = new SqliteStoreLifecycle(context, sessionSecret);

      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, lifecycle::database);
      IllegalStateException repeatedFailure =
          assertThrows(IllegalStateException.class, lifecycle::database);

      assertInstanceOf(SqliteStorageFailureException.class, firstFailure);
      assertTrue(
          NullTestSupport.messageOf(firstFailure)
              .contains("Failed to open SQLite book connection. SQLITE_ERROR: open-boom"),
          () -> NullTestSupport.messageOf(firstFailure));
      assertSame(firstFailure, repeatedFailure);
    }
  }

  @Test
  void accessModes_enforceWritableBoundariesAndQueryOnlyPolicy() throws Exception {
    assertEquals(1, SqliteStoreAccessMode.READ_ONLY.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_EXISTING.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_CREATE.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.PLAN_EXECUTION.queryOnlyPragmaValue());
    assertThrows(
        IllegalStateException.class, SqliteStoreAccessMode.READ_ONLY::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableMutation);
    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_ONLY::requireWritableInitialization);
    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableInitialization);
    assertTrue(SqliteStoreAccessMode.READ_ONLY.defersMissingBookOpen());
    assertTrue(SqliteStoreAccessMode.READ_WRITE_EXISTING.defersMissingBookOpen());
    assertFalse(SqliteStoreAccessMode.READ_WRITE_CREATE.defersMissingBookOpen());
    assertTrue(SqliteStoreAccessMode.PLAN_EXECUTION.defersMissingBookOpen());
    Path existingBookPath = tempDirectory.resolve("read-write-existing.sqlite");
    initializeBookOnDisk(existingBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                existingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("3000"),
                  new AccountName("Equity"),
                  dev.erst.fingrind.core.AccountType.REVENUE,
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("3000"),
              new AccountName("Equity"),
              dev.erst.fingrind.core.AccountType.REVENUE,
              NormalBalance.CREDIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          "delete", queryText(requireStoreDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(requireStoreDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(requireStoreDatabase(postingFactStore), "pragma query_only"));
    }
    Path missingBookPath = tempDirectory.resolve("read-write-existing-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode missing", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                missingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertEquals(
          "This FinGrind SQLite session cannot initialize or create a book file.",
          exception.getMessage());
    }
  }

  private void assertProtectedBookVerificationFailure(Path bookPath) {
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void helperBoundaries_rejectUnsafeShapesAndWrapNativeFailures() throws Exception {
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
    Path blankBookPath = tempDirectory.resolve("helper-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    withStandaloneDatabase(
        bookAccess(blankBookPath),
        database -> {
          IllegalStateException emptyQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleInt(database, "select 1 where 0"));
          assertEquals(
              "SQLite integer query returned no rows: select 1 where 0",
              emptyQueryException.getMessage());
          IllegalStateException emptyTextQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleText(database, "select 'x' where 0"));
          assertEquals(
              "SQLite text query returned no rows: select 'x' where 0",
              emptyTextQueryException.getMessage());
          try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
            IllegalStateException blankException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not initialized as a FinGrind book.",
                blankException.getMessage());
          }
        });
    Path initializedBookPath = tempDirectory.resolve("helper-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          IllegalStateException multiRowException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.queryOptionalInt(
                          database, "select 1 union all select 2"));
          assertEquals(
              "SQLite integer query returned more than one row: select 1 union all select 2",
              multiRowException.getMessage());
          IllegalStateException multiRowTextException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.querySingleText(
                          database, "select 'x' union all select 'y'"));
          assertEquals(
              "SQLite text query returned more than one row: select 'x' union all select 'y'",
              multiRowTextException.getMessage());
          assertEquals(
              OptionalInt.of(1), SqliteStatementQueries.queryOptionalInt(database, "select 1"));
          assertEquals("x", SqliteStatementQueries.querySingleText(database, "select 'x'"));
          assertEquals("INITIALIZED_FINGRIND", bookStateReader.bookState(database).toString());
        });
    Path foreignBookPath = tempDirectory.resolve("helper-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    withStandaloneDatabase(
        bookAccess(foreignBookPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(foreignBookPath))) {
            IllegalStateException foreignException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not a FinGrind book.", foreignException.getMessage());
          }
        });
    Path unsupportedBookPath = tempDirectory.resolve("helper-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    int unsupportedVersion = SqliteBookContract.FORMAT_VERSION + 1;
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = " + unsupportedVersion));
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> {
          assertEquals(
              "UNSUPPORTED_FINGRIND_VERSION", bookStateReader.bookState(database).toString());
        });
    Path incompleteBookPath = tempDirectory.resolve("helper-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    withStandaloneDatabase(
        bookAccess(incompleteBookPath),
        database -> {
          assertEquals("INCOMPLETE_FINGRIND", bookStateReader.bookState(database).toString());
        });
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      setStoreDatabase(postingFactStore, openNativeDatabase(bookAccess(blankBookPath)));
      assertEquals(
          Optional.of(
              new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                  .BookNotInitialized()),
          POSTING_ACCEPTANCE_POLICY.rejectionFor(
              postingDraft("posting-helper", "idem-helper", Optional.empty(), Optional.empty()),
              new SqliteTransactionValidationBook(
                  requireStoreDatabase(postingFactStore), postingFactStore.postingReader())));
    }
    Path staleBookPath = tempDirectory.resolve("find-one-stale.sqlite");
    createEmptySqliteFile(staleBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(staleBookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(staleBookPath));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-helper")));
      assertTrue(NullTestSupport.messageOf(failure).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void activeNativeDatabase_returnsPublishedSessionHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("active-native-database.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(storeDatabase(postingFactStore), postingFactStore.activeNativeDatabase());
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void lifecycleStateModel_coversFailedClosedAndHelperFallbackBranches() throws Exception {
    Path bookPath = tempDirectory.resolve("lifecycle-state-model.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      assertDoesNotThrow(postingFactStore.lifecycle::ensureOpenSession);
      initializeBookWithDefaultAccounts(postingFactStore);
      SqliteStoreLifecycle lifecycle = postingFactStore.lifecycle;
      SqliteNativeDatabase database = requireStoreDatabase(postingFactStore);
      assertDoesNotThrow(lifecycle::ensureOpenSession);
      SqliteBookStateSnapshot snapshot =
          new SqliteBookStateSnapshot(1, 1, SqliteBookState.INITIALIZED_FINGRIND);
      SqliteBookStateSnapshot replacementSnapshot =
          new SqliteBookStateSnapshot(2, 2, SqliteBookState.UNSUPPORTED_FINGRIND_VERSION);
      IllegalStateException failedState = new IllegalStateException("failed-state");
      IllegalStateException closedFailure = new IllegalStateException("closed-failure");

      exerciseIdleStateBranches(postingFactStore, lifecycle, snapshot);
      exerciseOpenedStateBranches(lifecycle, database, snapshot);
      exerciseFailedStateBranches(
          postingFactStore, lifecycle, database, snapshot, replacementSnapshot, failedState);
      exerciseClosedStateBranches(postingFactStore, lifecycle, database, snapshot, closedFailure);
      exerciseRejectedFailureFallbackBranches(lifecycle);
      setLifecycleSessionState(
          lifecycle, lifecycleSessionState("OpenedSession", database, replacementSnapshot));
    }
  }

  private static void exerciseIdleStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteBookStateSnapshot snapshot) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("IdleSession", snapshot));
    lifecycle.clearCachedState();
    assertEquals(null, invokeCachedBookState(lifecycle));
    lifecycle.clearDatabaseState();
    assertEquals(null, postingFactStore.lifecycle.publishedDatabase());
    assertEquals(null, invokeDetachPublishedDatabase(lifecycle));
  }

  private static void exerciseOpenedStateBranches(
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("OpenedSession", database, snapshot));
    lifecycle.publishDatabase(database);
    IllegalStateException rememberedOpenedFailure = new IllegalStateException("opened-failure");
    assertSame(
        rememberedOpenedFailure, invokeRememberTerminalFailure(lifecycle, rememberedOpenedFailure));
    assertSame(
        rememberedOpenedFailure,
        assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  private static void exerciseFailedStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot,
      SqliteBookStateSnapshot replacementSnapshot,
      IllegalStateException failedState) {
    setLifecycleSessionState(
        lifecycle, lifecycleSessionState("FailedSession", database, snapshot, failedState));
    lifecycle.cacheState(replacementSnapshot);
    assertEquals(replacementSnapshot, invokeCachedBookState(lifecycle));
    lifecycle.clearCachedState();
    assertEquals(null, invokeCachedBookState(lifecycle));
    lifecycle.publishDatabase(database);
    assertSame(database, postingFactStore.lifecycle.publishedDatabase());
    assertSame(database, invokeDetachPublishedDatabase(lifecycle));
    lifecycle.clearDatabaseState();
    assertEquals(null, postingFactStore.lifecycle.publishedDatabase());
    assertSame(
        failedState, assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession));
    IllegalStateException rememberedFailedFailure =
        new IllegalStateException("failed-state-replaced");
    assertSame(
        rememberedFailedFailure, invokeRememberTerminalFailure(lifecycle, rememberedFailedFailure));
    assertSame(
        rememberedFailedFailure,
        assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  private static void exerciseClosedStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot,
      IllegalStateException closedFailure) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("ClosedSession", (Object) null));
    lifecycle.cacheState(snapshot);
    lifecycle.clearCachedState();
    lifecycle.clearDatabaseState();
    lifecycle.publishDatabase(database);
    assertEquals(null, postingFactStore.lifecycle.publishedDatabase());
    assertEquals(null, invokeCachedBookState(lifecycle));
    assertEquals(null, invokeDetachPublishedDatabase(lifecycle));
    IllegalStateException closedWithoutFailure =
        assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession);
    assertEquals("SQLite book session is already closed.", closedWithoutFailure.getMessage());

    setLifecycleSessionState(lifecycle, lifecycleSessionState("ClosedSession", closedFailure));
    assertSame(
        closedFailure, assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession));
    IllegalStateException rememberedClosedFailure = new IllegalStateException("closed-replaced");
    assertSame(
        rememberedClosedFailure, invokeRememberTerminalFailure(lifecycle, rememberedClosedFailure));
    assertSame(
        rememberedClosedFailure,
        assertThrows(IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  private static void exerciseRejectedFailureFallbackBranches(SqliteStoreLifecycle lifecycle) {
    ContractFailureException storedContractFailure =
        new ContractFailureException(
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected.", null, null));
    setLifecycleSessionState(
        lifecycle, lifecycleSessionState("FailedSession", null, null, storedContractFailure));
    assertSame(
        storedContractFailure,
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected.", null, null)));
    setLifecycleSessionState(
        lifecycle,
        lifecycleSessionState(
            "FailedSession", null, null, new IllegalStateException("plain-failed")));
    ContractFailureException failedFallback =
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected plain failed.", null, null));
    assertEquals("Rejected plain failed.", failedFallback.failure().message());

    setLifecycleSessionState(lifecycle, lifecycleSessionState("IdleSession", (Object) null));
    ContractFailureException idleFallback =
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected fallback.", null, null));
    assertEquals("Rejected fallback.", idleFallback.failure().message());
  }

  private static Object lifecycleSessionState(String simpleName, @Nullable Object... arguments) {
    MethodHandle constructor =
        switch (simpleName) {
          case "IdleSession" ->
              lifecycleConstructorHandle(
                  IDLE_SESSION_CLASS,
                  MethodType.methodType(void.class, SqliteBookStateSnapshot.class));
          case "OpenedSession" ->
              lifecycleConstructorHandle(
                  OPENED_SESSION_CLASS,
                  MethodType.methodType(
                      void.class, SqliteNativeDatabase.class, SqliteBookStateSnapshot.class));
          case "FailedSession" ->
              lifecycleConstructorHandle(
                  FAILED_SESSION_CLASS,
                  MethodType.methodType(
                      void.class,
                      SqliteNativeDatabase.class,
                      SqliteBookStateSnapshot.class,
                      IllegalStateException.class));
          case "ClosedSession" ->
              lifecycleConstructorHandle(
                  CLOSED_SESSION_CLASS,
                  MethodType.methodType(void.class, IllegalStateException.class));
          default ->
              throw new IllegalArgumentException("Unknown lifecycle state type: " + simpleName);
        };
    return invokeHandle(constructor, arguments);
  }

  private static void setLifecycleSessionState(
      SqliteStoreLifecycle lifecycle, Object sessionState) {
    SESSION_STATE_HANDLE.set(lifecycle, sessionState);
  }

  private static @Nullable SqliteBookStateSnapshot invokeCachedBookState(
      SqliteStoreLifecycle lifecycle) {
    return (@Nullable SqliteBookStateSnapshot) invokeHandle(CACHED_BOOK_STATE_HANDLE, lifecycle);
  }

  private static @Nullable SqliteNativeDatabase invokeDetachPublishedDatabase(
      SqliteStoreLifecycle lifecycle) {
    return (@Nullable SqliteNativeDatabase)
        invokeHandle(DETACH_PUBLISHED_DATABASE_HANDLE, lifecycle);
  }

  private static IllegalStateException invokeRememberTerminalFailure(
      SqliteStoreLifecycle lifecycle, IllegalStateException failure) {
    return (IllegalStateException)
        invokeHandle(REMEMBER_TERMINAL_FAILURE_HANDLE, lifecycle, failure);
  }

  private static ContractFailureException invokeRememberedRejectedFailure(
      SqliteStoreLifecycle lifecycle, dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    return (ContractFailureException)
        invokeHandle(REMEMBERED_REJECTED_FAILURE_HANDLE, lifecycle, failure);
  }

  private static Object invokeHandle(MethodHandle handle, @Nullable Object... arguments) {
    try {
      return handle.invokeWithArguments(arguments);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Unexpected checked throwable from lifecycle handle.", throwable);
    }
  }

  private static MethodHandle lifecycleConstructorHandle(Class<?> declaringClass, MethodType type) {
    try {
      return LIFECYCLE_LOOKUP.findConstructor(declaringClass, type);
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static MethodHandle lifecycleMethodHandle(String methodName, MethodType type) {
    try {
      return LIFECYCLE_LOOKUP.findVirtual(SqliteStoreLifecycle.class, methodName, type);
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static VarHandle lifecycleSessionStateHandle() {
    try {
      return LIFECYCLE_LOOKUP.findVarHandle(
          SqliteStoreLifecycle.class, "sessionState", SESSION_STATE_CLASS);
    } catch (NoSuchFieldException | IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static MethodHandles.Lookup lifecycleLookup() {
    try {
      return MethodHandles.privateLookupIn(SqliteStoreLifecycle.class, MethodHandles.lookup());
    } catch (IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static Class<?> lifecycleNestedType(String simpleName) {
    for (Class<?> nestedType : SqliteStoreLifecycle.class.getDeclaredClasses()) {
      if (nestedType.getSimpleName().equals(simpleName)) {
        return nestedType;
      }
    }
    throw new ExceptionInInitializerError("Missing lifecycle nested type: " + simpleName);
  }
}
