package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Access-mode and lifecycle-state-model coverage for {@link SqlitePostingFactStore}. */
class SqliteStoreAccessModeAndStateModelTest extends SqliteStoreLifecycleTestSupport {
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
        database ->
            assertEquals(
                "UNSUPPORTED_FINGRIND_VERSION", bookStateReader.bookState(database).toString()));
    Path incompleteBookPath = tempDirectory.resolve("helper-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    withStandaloneDatabase(
        bookAccess(incompleteBookPath),
        database ->
            assertEquals("INCOMPLETE_FINGRIND", bookStateReader.bookState(database).toString()));
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
}
