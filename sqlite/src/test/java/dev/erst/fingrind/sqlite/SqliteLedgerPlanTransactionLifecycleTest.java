package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused coverage for isolated ledger-plan lifecycle and execution branches. */
class SqliteLedgerPlanTransactionLifecycleTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void attestedPlan_defersMissingBookOpeningWithoutCreatingCallerArtifacts() {
    Path bookPath = tempDirectory.resolve("deferred-plan").resolve("nested").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.PLAN_EXECUTION);
    AtomicInteger databaseOpenCalls = new AtomicInteger();

    transaction
        .lifecycle()
        .beginAttestedPlan(
            "deferred-plan",
            planAuthorizer(),
            () -> {
              databaseOpenCalls.incrementAndGet();
              throw new AssertionError("Deferred missing-book plans must not open the database.");
            });

    assertTrue(transaction.lifecycle().active());
    assertFalse(transaction.lifecycle().begunInDatabase());
    assertFalse(Files.exists(bookPath));

    assertEquals(0, databaseOpenCalls.get());
    transaction.lifecycle().rollback(null);
    assertFalse(transaction.lifecycle().active());
  }

  @Test
  void readOnlyPlan_usesAReadSnapshotRejectsDirectWritesAndCommitsWithoutAnAttestationHead()
      throws Exception {
    Path existingBookPath = tempDirectory.resolve("existing.sqlite");
    Files.writeString(existingBookPath, "existing book placeholder");
    PlanTransaction transaction = transaction(existingBookPath, SqliteStoreAccessMode.READ_ONLY);
    try (RecordingDatabase database = new RecordingDatabase()) {
      transaction.lifecycle().beginReadOnlyPlan("read-only-plan", () -> database);

      assertTrue(transaction.lifecycle().active());
      assertTrue(transaction.lifecycle().begunInDatabase());
      assertEquals(List.of("begin"), database.statements);
      assertThrows(
          IllegalStateException.class, transaction.execution()::requireDirectMutationPermitted);
      assertThrows(
          IllegalStateException.class, transaction.execution()::requireObservedAttestationHead);
      transaction.execution().enterPlanStep(0);
      transaction.execution().enterPlanStep(1);
      assertThrows(IllegalStateException.class, () -> transaction.execution().enterPlanStep(1));

      transaction.lifecycle().commit(() -> database);

      assertEquals(List.of("begin", "commit"), database.statements);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void readOnlyPlan_neverOpensOrCreatesAMissingBook() {
    Path bookPath = tempDirectory.resolve("missing").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_ONLY);

    transaction
        .lifecycle()
        .beginReadOnlyPlan(
            "missing-read-only-plan",
            () -> {
              throw new AssertionError("A missing read-only plan must not open SQLite.");
            });
    transaction
        .lifecycle()
        .commit(
            () -> {
              throw new AssertionError("A missing read-only plan must not open SQLite on commit.");
            });

    assertFalse(Files.exists(bookPath));
    assertFalse(Files.exists(bookPath.getParent()));
    assertFalse(transaction.lifecycle().active());
  }

  @Test
  void attestedPlan_openFailureResetsTheLifecycleWithoutUnlinkingTheCallerPath() throws Exception {
    Path bookPath = tempDirectory.resolve("failed-open").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                transaction
                    .lifecycle()
                    .beginAttestedPlan(
                        "failed-open-plan",
                        planAuthorizer(),
                        () -> {
                          writeProvisionalArtifacts(bookPath);
                          replaceWithExternalArtifacts(bookPath);
                          throw new IllegalStateException("open failed");
                        }));

    assertEquals("open failed", failure.getMessage());
    assertFalse(transaction.lifecycle().active());
    assertRetainedExternalArtifacts(bookPath);
  }

  @Test
  void readOnlyPlan_wrapsCommitFailureAndRetainsARecoverableActiveTransaction() throws Exception {
    Path bookPath = tempDirectory.resolve("commit-failure.sqlite");
    Files.writeString(bookPath, "existing book placeholder");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_ONLY);
    try (RecordingDatabase database = new RecordingDatabase()) {
      transaction.lifecycle().beginReadOnlyPlan("commit-failure-plan", () -> database);
      database.failure =
          new SqliteNativeException(SqliteNativeResultCode.code("ERROR"), "commit failure");

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class, () -> transaction.lifecycle().commit(() -> database));

      assertEquals(
          "Failed to commit SQLite ledger plan transaction. SQLITE_ERROR: commit failure",
          failure.getMessage());
      assertTrue(transaction.lifecycle().active());
      database.failure = null;
      transaction.lifecycle().rollback(database);
      assertEquals(List.of("begin", "commit", "rollback"), database.statements);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void readOnlyPlan_commitPreservesOneCanonicalContractFailureFromItsSessionSupplier()
      throws Exception {
    Path bookPath = tempDirectory.resolve("commit-contract-failure.sqlite");
    Files.writeString(bookPath, "existing book placeholder");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_ONLY);
    ContractFailureException primaryFailure =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));
    try (RecordingDatabase database = new RecordingDatabase()) {
      transaction.lifecycle().beginReadOnlyPlan("commit-contract-failure-plan", () -> database);

      assertSame(
          primaryFailure,
          assertThrows(
              ContractFailureException.class,
              () ->
                  transaction
                      .lifecycle()
                      .commit(
                          () -> {
                            throw primaryFailure;
                          })));
      assertTrue(transaction.lifecycle().active());

      transaction.lifecycle().rollback(database);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void readOnlyPlan_beginFailureResetsTheLifecycleBeforeTheCallerCanRetry() throws Exception {
    Path bookPath = tempDirectory.resolve("begin-failure.sqlite");
    Files.writeString(bookPath, "existing book placeholder");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_ONLY);
    try (RecordingDatabase database = new RecordingDatabase()) {
      IllegalStateException beginFailure = new IllegalStateException("read begin failed");
      database.failure = beginFailure;

      assertSame(
          beginFailure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  transaction
                      .lifecycle()
                      .beginReadOnlyPlan("failed-read-only-plan", () -> database)));
      assertEquals(List.of("begin"), database.statements);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void deferredPlanWithACompletedChildCannotCommitWithoutAnAggregateAttestation() {
    Path bookPath = tempDirectory.resolve("deferred-missing-aggregate").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.PLAN_EXECUTION);
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

    transaction
        .lifecycle()
        .beginAttestedPlan(
            "deferred-missing-aggregate-plan",
            authorizer,
            () -> {
              throw new AssertionError("A deferred missing-book plan must not open SQLite.");
            });
    transaction.execution().enterPlanStep(0);
    transaction
        .execution()
        .recordCompletedPlanChild(
            authorizer,
            "record-posting",
            new AttestationOperationPreimages(new byte[] {1}, new byte[] {2}));

    assertThrows(
        IllegalStateException.class,
        () ->
            transaction
                .lifecycle()
                .commit(
                    () -> {
                      throw new AssertionError(
                          "A deferred plan must not open SQLite on forced rollback.");
                    }));
    assertFalse(transaction.lifecycle().active());
  }

  @Test
  void readOnlyPlan_wrapsOneRuntimeCommitFailureAndKeepsRollbackAvailable() throws Exception {
    Path bookPath = tempDirectory.resolve("runtime-commit-failure.sqlite");
    Files.writeString(bookPath, "existing book placeholder");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.READ_ONLY);
    try (RecordingDatabase database = new RecordingDatabase()) {
      transaction.lifecycle().beginReadOnlyPlan("runtime-commit-failure-plan", () -> database);
      IllegalStateException commitFailure = new IllegalStateException("commit runtime failure");
      database.failure = commitFailure;

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class, () -> transaction.lifecycle().commit(() -> database));

      assertEquals("Failed to commit SQLite ledger plan transaction.", failure.getMessage());
      assertSame(commitFailure, failure.getCause());
      assertTrue(transaction.lifecycle().active());
      database.failure = null;
      transaction.lifecycle().rollback(database);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void lifecycleNoOpsAndDeferredReadOnlyPlansDoNotInventSQLiteTransactions() {
    Path bookPath = tempDirectory.resolve("deferred-read-only").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.PLAN_READ_ONLY);
    try (RecordingDatabase database = new RecordingDatabase()) {
      transaction.lifecycle().rollback(null);
      transaction.lifecycle().beginImmediateIfNeeded(database);
      assertEquals(List.of(), database.statements);

      transaction
          .lifecycle()
          .beginReadOnlyPlan(
              "deferred-read-only-plan",
              () -> {
                throw new AssertionError("A missing read-only plan must not open SQLite.");
              });
      assertFalse(transaction.lifecycle().begunInDatabase());
      transaction.lifecycle().beginImmediateIfNeeded(database);
      transaction.lifecycle().beginImmediateIfNeeded(database);
      assertEquals(List.of("begin immediate"), database.statements);
      transaction.lifecycle().rollback(null);
      assertFalse(transaction.lifecycle().active());
    }
  }

  @Test
  void lifecycleRejectsASecondPlanBeforeTheFirstPlanCloses() {
    Path bookPath = tempDirectory.resolve("duplicate-plan").resolve("book.sqlite");
    PlanTransaction transaction = transaction(bookPath, SqliteStoreAccessMode.PLAN_EXECUTION);
    AttestationPlanOperationAuthorizer authorizer = planAuthorizer();

    transaction
        .lifecycle()
        .beginAttestedPlan(
            "first-plan",
            authorizer,
            () -> {
              throw new AssertionError("A deferred missing-book plan must not open SQLite.");
            });

    assertThrows(
        IllegalStateException.class,
        () -> transaction.lifecycle().beginAttestedPlan("second-plan", authorizer, () -> null));
    transaction.lifecycle().rollback(null);
  }

  private PlanTransaction transaction(Path bookPath, SqliteStoreAccessMode accessMode) {
    SqliteLedgerPlanTransactionStateHolder state = new SqliteLedgerPlanTransactionStateHolder();
    return new PlanTransaction(
        new SqliteLedgerPlanTransactionLifecycle(
            new SqliteStoreContext(bookPath, accessMode, SqliteNativeBootstrap::api), state),
        new SqliteLedgerPlanExecution(state));
  }

  private static AttestationPlanOperationAuthorizer planAuthorizer() {
    return new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
  }

  private record PlanTransaction(
      SqliteLedgerPlanTransactionLifecycle lifecycle, SqliteLedgerPlanExecution execution) {}

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

  private static void writeProvisionalArtifacts(Path bookPath) {
    try {
      Files.writeString(bookPath, "FinGrind provisional book");
      String baseName = bookPath.getFileName().toString();
      Files.writeString(bookPath.resolveSibling(baseName + "-journal"), "FinGrind journal");
      Files.writeString(bookPath.resolveSibling(baseName + "-wal"), "FinGrind WAL");
      Files.writeString(bookPath.resolveSibling(baseName + "-shm"), "FinGrind SHM");
    } catch (java.io.IOException exception) {
      throw new AssertionError("Unable to establish provisional caller artifacts.", exception);
    }
  }

  private static void replaceWithExternalArtifacts(Path bookPath) {
    try {
      String baseName = bookPath.getFileName().toString();
      Files.writeString(bookPath, "external replacement book");
      Files.writeString(bookPath.resolveSibling(baseName + "-journal"), "external journal");
      Files.writeString(bookPath.resolveSibling(baseName + "-wal"), "external WAL");
      Files.writeString(bookPath.resolveSibling(baseName + "-shm"), "external SHM");
      Files.writeString(
          Objects.requireNonNull(bookPath.getParent(), "bookPath parent")
              .resolve("external-parent-content"),
          "external parent");
    } catch (java.io.IOException exception) {
      throw new AssertionError("Unable to replace provisional caller artifacts.", exception);
    }
  }

  private static void assertRetainedExternalArtifacts(Path bookPath) throws java.io.IOException {
    String baseName = bookPath.getFileName().toString();
    assertEquals("external replacement book", Files.readString(bookPath));
    assertEquals(
        "external journal", Files.readString(bookPath.resolveSibling(baseName + "-journal")));
    assertEquals("external WAL", Files.readString(bookPath.resolveSibling(baseName + "-wal")));
    assertEquals("external SHM", Files.readString(bookPath.resolveSibling(baseName + "-shm")));
    Path parent = Objects.requireNonNull(bookPath.getParent(), "bookPath parent");
    assertTrue(Files.isDirectory(parent));
    assertTrue(Files.exists(parent.resolve("external-parent-content")));
  }
}
