package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused coverage for isolated ledger-plan transaction coordination branches. */
class SqliteLedgerPlanTransactionCoordinatorTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void noteBookArtifactsMayMutate_isIdempotentForDeferredMissingBookTransactions() {
    Path bookPath = tempDirectory.resolve("deferred-plan").resolve("nested").resolve("book.sqlite");
    SqliteLedgerPlanTransactionCoordinator coordinator =
        new SqliteLedgerPlanTransactionCoordinator(
            new SqliteStoreContext(
                bookPath, SqliteStoreAccessMode.PLAN_EXECUTION, SqliteNativeBootstrap::api));
    AtomicInteger databaseOpenCalls = new AtomicInteger();

    coordinator.begin(
        () -> {
          databaseOpenCalls.incrementAndGet();
          throw new AssertionError(
              "Deferred missing-book transactions must not open the database.");
        },
        ignored -> {});

    assertTrue(coordinator.active());
    assertFalse(coordinator.begunInDatabase());
    assertFalse(coordinator.createdBookArtifacts());

    coordinator.noteBookArtifactsMayMutate();
    coordinator.noteBookArtifactsMayMutate();

    assertEquals(0, databaseOpenCalls.get());
    assertTrue(coordinator.createdBookArtifacts());
    assertEquals(
        tempDirectory.toAbsolutePath().normalize(), coordinator.preexistingAncestorDirectory());

    NullablePathBox cleanedAncestorDirectory = new NullablePathBox();
    coordinator.rollback(
        null, ancestorDirectory -> cleanedAncestorDirectory.value = ancestorDirectory);

    assertEquals(tempDirectory.toAbsolutePath().normalize(), cleanedAncestorDirectory.value);
    assertFalse(coordinator.active());
    assertFalse(coordinator.createdBookArtifacts());
  }

  @Test
  void coordinator_coversEagerDeferredFailureAndRollbackTransactionTransitions() throws Exception {
    Path existingBookPath = tempDirectory.resolve("existing.sqlite");
    Files.writeString(existingBookPath, "existing book placeholder");
    SqliteLedgerPlanTransactionCoordinator eager =
        new SqliteLedgerPlanTransactionCoordinator(
            new SqliteStoreContext(
                existingBookPath,
                SqliteStoreAccessMode.READ_WRITE_CREATE,
                SqliteNativeBootstrap::api));
    try (RecordingDatabase database = new RecordingDatabase()) {

      eager.begin(
          () -> database, ignored -> fail("existing-book transaction must not clean artifacts"));
      assertTrue(eager.active());
      assertFalse(eager.begunInDatabase());
      assertFalse(eager.createdBookArtifacts());
      assertNull(eager.preexistingAncestorDirectory());
      assertThrows(IllegalStateException.class, () -> eager.begin(() -> database, ignored -> {}));
      eager.noteBookArtifactsMayMutate();
      assertFalse(eager.createdBookArtifacts());
      eager.beginImmediateIfNeeded(database);
      eager.beginImmediateIfNeeded(database);
      assertEquals(List.of("begin immediate"), database.statements);
      assertTrue(eager.begunInDatabase());
      eager.commit(() -> database);
      assertEquals(List.of("begin immediate", "commit"), database.statements);
      assertFalse(eager.active());
      assertFalse(eager.begunInDatabase());
      assertThrows(IllegalStateException.class, () -> eager.commit(() -> database));

      Path deferredBookPath = tempDirectory.resolve("deferred-commit.sqlite");
      SqliteLedgerPlanTransactionCoordinator deferred =
          new SqliteLedgerPlanTransactionCoordinator(
              new SqliteStoreContext(
                  deferredBookPath,
                  SqliteStoreAccessMode.PLAN_EXECUTION,
                  SqliteNativeBootstrap::api));
      deferred.begin(
          () -> {
            throw new AssertionError("Deferred missing-book commit must not open SQLite.");
          },
          ignored -> {});
      deferred.commit(
          () -> {
            throw new AssertionError("Deferred missing-book commit must not open SQLite.");
          });
      assertFalse(deferred.active());
      deferred.rollback(null, ignored -> fail("Inactive transaction must not clean artifacts"));

      Path missingBookPath = tempDirectory.resolve("rollback").resolve("book.sqlite");
      SqliteLedgerPlanTransactionCoordinator rollback =
          new SqliteLedgerPlanTransactionCoordinator(
              new SqliteStoreContext(
                  missingBookPath,
                  SqliteStoreAccessMode.READ_WRITE_CREATE,
                  SqliteNativeBootstrap::api));
      NullablePathBox rollbackCleanup = new NullablePathBox();
      rollback.begin(() -> database, ancestor -> rollbackCleanup.value = ancestor);
      rollback.beginImmediateIfNeeded(database);
      rollback.rollback(database, ancestor -> rollbackCleanup.value = ancestor);
      assertEquals(tempDirectory.toAbsolutePath().normalize(), rollbackCleanup.value);
      assertFalse(rollback.active());
      assertFalse(rollback.createdBookArtifacts());
      assertEquals(
          List.of("begin immediate", "commit", "begin immediate", "rollback"), database.statements);

      SqliteLedgerPlanTransactionCoordinator failedOpen =
          new SqliteLedgerPlanTransactionCoordinator(
              new SqliteStoreContext(
                  tempDirectory.resolve("failed-open").resolve("book.sqlite"),
                  SqliteStoreAccessMode.READ_WRITE_CREATE,
                  SqliteNativeBootstrap::api));
      NullablePathBox failedOpenCleanup = new NullablePathBox();
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  failedOpen.begin(
                      () -> {
                        throw new IllegalStateException("open failed");
                      },
                      ancestor -> failedOpenCleanup.value = ancestor));
      assertEquals("open failed", failure.getMessage());
      assertEquals(tempDirectory.toAbsolutePath().normalize(), failedOpenCleanup.value);
      assertFalse(failedOpen.active());

      SqliteLedgerPlanTransactionCoordinator failedCleanup =
          new SqliteLedgerPlanTransactionCoordinator(
              new SqliteStoreContext(
                  tempDirectory.resolve("failed-cleanup").resolve("book.sqlite"),
                  SqliteStoreAccessMode.READ_WRITE_CREATE,
                  SqliteNativeBootstrap::api));
      IllegalStateException openingFailure = new IllegalStateException("opening failed");
      IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");
      assertSame(
          openingFailure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  failedCleanup.begin(
                      () -> {
                        throw openingFailure;
                      },
                      ignored -> {
                        throw cleanupFailure;
                      })));
      assertEquals(List.of(cleanupFailure), List.of(openingFailure.getSuppressed()));
      assertFalse(failedCleanup.active());
    }
  }

  @Test
  void coordinator_wrapsCommitFailureAndRetainsARecoverableActiveTransaction() throws Exception {
    Path bookPath = tempDirectory.resolve("commit-failure.sqlite");
    Files.writeString(bookPath, "existing book placeholder");
    SqliteLedgerPlanTransactionCoordinator coordinator =
        new SqliteLedgerPlanTransactionCoordinator(
            new SqliteStoreContext(
                bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api));
    try (RecordingDatabase database = new RecordingDatabase()) {
      coordinator.begin(() -> database, ignored -> {});
      coordinator.beginImmediateIfNeeded(database);
      database.failure =
          new SqliteNativeException(SqliteNativeResultCode.code("ERROR"), "commit failure");

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> coordinator.commit(() -> database));
      assertEquals(
          "Failed to commit SQLite ledger plan transaction. SQLITE_ERROR: commit failure",
          failure.getMessage());
      assertTrue(coordinator.active());
      assertTrue(coordinator.begunInDatabase());
      database.failure = null;
      coordinator.rollback(database, ignored -> {});
      assertFalse(coordinator.active());

      coordinator.begin(() -> database, ignored -> {});
      coordinator.beginImmediateIfNeeded(database);
      IllegalStateException commitStateFailure = new IllegalStateException("commit state failure");
      database.failure = commitStateFailure;
      IllegalStateException wrappedStateFailure =
          assertThrows(IllegalStateException.class, () -> coordinator.commit(() -> database));
      assertEquals(
          "Failed to commit SQLite ledger plan transaction.", wrappedStateFailure.getMessage());
      assertSame(commitStateFailure, wrappedStateFailure.getCause());
      database.failure = null;
      coordinator.rollback(null, ignored -> {});
      assertFalse(coordinator.active());
    }
  }

  /** Records transaction-control statements without requiring a native SQLite handle. */
  private static final class RecordingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();
    private @Nullable RuntimeException failure;

    private RecordingDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    public void close() {}

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
      if (failure != null) {
        throw failure;
      }
    }
  }

  /** Captures one nullable cleanup callback argument for assertions. */
  private static final class NullablePathBox {
    private @Nullable Path value;
  }
}
