package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Covers rollback and failure translation while opening a SQLite-backed book. */
class SqliteStoreBookOpeningOperationsTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void openAttestedBook_translatesAndRollsBackANativeSchemaInitializationFailure() {
    Path bookPath = tempDirectory.resolve("native-open-failure.sqlite");
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters("native open", TEST_BOOK_KEY.toCharArray()));
        SchemaFailingDatabase database = new SchemaFailingDatabase()) {
      SqliteStoreContext context =
          new SqliteStoreContext(
              bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, sessionSecret) {
            @Override
            SqliteNativeDatabase database() {
              return database;
            }

            @Override
            SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
              return new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE);
            }
          };
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(context, lifecycle);

      SqliteStorageFailureException failure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  operations.openAttestedBook(
                      initializedAt,
                      SqlitePostingFactFixtureSupport.bookIdentity(),
                      List.of(),
                      SqliteAttestationTestSupport.genesis(
                          SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt)));

      assertEquals(
          "Failed to initialize SQLite book. SQLITE_IOERR: simulated schema initialization failure",
          failure.getMessage());
      assertEquals(List.of("begin immediate", "rollback"), database.statements);
      lifecycle.close();
    }
  }

  @Test
  void openAttestedBook_rollsBackAndRethrowsOneRuntimeSchemaInitializationFailure() {
    Path bookPath = tempDirectory.resolve("runtime-open-failure.sqlite");
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    IllegalStateException schemaFailure =
        new IllegalStateException("simulated schema runtime failure");
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters("runtime open", TEST_BOOK_KEY.toCharArray()));
        RuntimeSchemaFailingDatabase database = new RuntimeSchemaFailingDatabase(schemaFailure)) {
      SqliteStoreContext context =
          new SqliteStoreContext(
              bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, sessionSecret) {
            @Override
            SqliteNativeDatabase database() {
              return database;
            }

            @Override
            SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
              return new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE);
            }
          };
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(context, lifecycle);

      assertEquals(
          schemaFailure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  operations.openAttestedBook(
                      initializedAt,
                      SqlitePostingFactFixtureSupport.bookIdentity(),
                      List.of(),
                      SqliteAttestationTestSupport.genesis(
                          SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt))));
      assertEquals(List.of("begin immediate", "rollback"), database.statements);
      lifecycle.close();
    }
  }

  @Test
  void openAttestedBook_rollsBackAnOutcomeProjectionFailureBeforeCommitAndAllowsRetry() {
    Path bookPath = tempDirectory.resolve("outcome-projection-failure.sqlite");
    Instant initializedAt = Instant.parse("2026-07-26T12:00:00Z");
    var bookIdentity = SqlitePostingFactFixtureSupport.bookIdentity();
    AttestationEvidence genesis = SqliteAttestationTestSupport.genesis(bookIdentity, initializedAt);
    IllegalStateException outcomeFailure =
        new IllegalStateException("simulated opening-outcome projection failure");

    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(
              store.storeContext(),
              store.storeLifecycle(),
              (ignoredInitializedAt,
                  ignoredBookIdentity,
                  ignoredGenesisEvidence,
                  ignoredVerification) -> {
                throw outcomeFailure;
              });

      assertSame(
          outcomeFailure,
          assertThrows(
              IllegalStateException.class,
              () -> operations.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis)));

      BookOpeningOutcome.Opened opened =
          assertInstanceOf(
              BookOpeningOutcome.Opened.class,
              store.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));
      assertEquals(initializedAt, opened.initializedAt());
    }
  }

  @Test
  void openAttestedBook_commitFailureWithAProvenBlankRollbackAllowsFounderRollbackAndRetry() {
    Path bookPath = tempDirectory.resolve("commit-rollback-proved-blank.sqlite");
    Instant initializedAt = Instant.parse("2026-07-26T12:05:00Z");
    var bookIdentity = SqlitePostingFactFixtureSupport.bookIdentity();
    AttestationEvidence genesis = SqliteAttestationTestSupport.genesis(bookIdentity, initializedAt);
    SqliteNativeException commitFailure =
        new SqliteNativeException(
            SqliteNativeResultCode.code("IOERR"), "simulated pre-durability commit failure");

    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(
              store.storeContext(),
              store.storeLifecycle(),
              (ignoredDatabase, ignoredTransactionOwnership) -> {
                throw commitFailure;
              });

      SqliteStorageFailureException failure =
          assertThrows(
              SqliteStorageFailureException.class,
              () -> operations.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));

      assertEquals(
          "Failed to initialize SQLite book. SQLITE_IOERR: simulated pre-durability commit failure",
          failure.getMessage());
      BookOpeningOutcome.Opened retried =
          assertInstanceOf(
              BookOpeningOutcome.Opened.class,
              store.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));
      assertEquals(initializedAt, retried.initializedAt());
    }
  }

  @Test
  void openAttestedBook_rethrowsNonNativeCommitFailureAfterItsProvedRollback() {
    Path bookPath = tempDirectory.resolve("commit-runtime-rollback-proved-blank.sqlite");
    Instant initializedAt = Instant.parse("2026-07-26T12:07:00Z");
    var bookIdentity = SqlitePostingFactFixtureSupport.bookIdentity();
    AttestationEvidence genesis = SqliteAttestationTestSupport.genesis(bookIdentity, initializedAt);
    IllegalStateException commitFailure =
        new IllegalStateException("simulated pre-durability runtime commit failure");

    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(
              store.storeContext(),
              store.storeLifecycle(),
              (ignoredDatabase, ignoredTransactionOwnership) -> {
                throw commitFailure;
              });

      assertSame(
          commitFailure,
          assertThrows(
              IllegalStateException.class,
              () -> operations.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis)));

      BookOpeningOutcome.Opened retried =
          assertInstanceOf(
              BookOpeningOutcome.Opened.class,
              store.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));
      assertEquals(initializedAt, retried.initializedAt());
    }
  }

  @Test
  void openAttestedBook_postCommitAcknowledgementFailureRetainsTheInitializedExclusiveBook()
      throws Exception {
    Path bookPath = tempDirectory.resolve("commit-acknowledgement-loss.sqlite");
    Instant initializedAt = Instant.parse("2026-07-26T12:10:00Z");
    var bookIdentity = SqlitePostingFactFixtureSupport.bookIdentity();
    AttestationEvidence genesis = SqliteAttestationTestSupport.genesis(bookIdentity, initializedAt);
    SqliteNativeException acknowledgementFailure =
        new SqliteNativeException(
            SqliteNativeResultCode.code("IOERR"), "simulated post-commit acknowledgement loss");

    try (SqlitePostingFactStore store =
        openStore(bookAccess(bookPath), SqliteStoreAccessMode.READ_WRITE_CREATE_EXCLUSIVE)) {
      SqliteStoreBookOpeningOperations operations =
          new SqliteStoreBookOpeningOperations(
              store.storeContext(),
              store.storeLifecycle(),
              (activeDatabase, transactionOwnership) -> {
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                throw acknowledgementFailure;
              });

      SqliteOpenBookCompletionUncertainException failure =
          assertThrows(
              SqliteOpenBookCompletionUncertainException.class,
              () -> operations.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));

      assertSame(acknowledgementFailure, failure.getCause());
      assertEquals(initializedAt, failure.openedBook().initializedAt());
      assertEquals(bookIdentity, failure.openedBook().bookIdentity());
      assertTrue(Files.exists(bookPath));
    }

    assertTrue(Files.exists(bookPath));
    try (SqlitePostingFactStore reopened = openStore(bookAccess(bookPath))) {
      assertInstanceOf(
          BookOpeningOutcome.Rejected.class,
          reopened.openAttestedBook(initializedAt, bookIdentity, List.of(), genesis));
    }
  }

  @Test
  void postCommitStateInspectionFailureRetainsTheCommitFailureAsDiagnosticEvidence() {
    IllegalStateException commitFailure = new IllegalStateException("commit acknowledgement lost");
    IllegalStateException inspectionFailure =
        new IllegalStateException("post-commit inspection failed");

    try (SchemaFailingDatabase database = new SchemaFailingDatabase()) {
      assertFalse(
          SqliteStoreBookOpeningOperations.hasProvedBlankBookAfterCommitFailure(
              database,
              commitFailure,
              ignoredDatabase -> {
                throw inspectionFailure;
              }));
    }

    assertEquals(List.of(inspectionFailure), List.of(commitFailure.getSuppressed()));
  }

  /** Records transaction control while making only schema execution fail natively. */
  private static final class SchemaFailingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();

    private SchemaFailingDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
    }

    @Override
    void executeScript(String sql) {
      throw new SqliteNativeException(
          SqliteNativeResultCode.code("IOERR"), "simulated schema initialization failure");
    }

    @Override
    public void close() {}
  }

  /** Records transaction control while making only schema execution fail with a runtime error. */
  private static final class RuntimeSchemaFailingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();
    private final IllegalStateException failure;

    private RuntimeSchemaFailingDatabase(IllegalStateException failure) {
      super(MemorySegment.NULL);
      this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
    }

    @Override
    void executeScript(String sql) {
      throw failure;
    }

    @Override
    public void close() {}
  }
}
