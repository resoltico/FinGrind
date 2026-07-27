package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Exercises lifecycle-level plan deferral, retained provisional artifacts, and opening failures.
 */
class SqliteStoreLifecycleFieldTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void attestedPlanTransaction_defersMissingBookOpeningUntilMutationAdmission() throws Exception {
    Path bookPath = tempDirectory.resolve("plan").resolve("book.sqlite");
    try (SqlitePostingFactStore store =
        openStore(bookAccess(bookPath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      assertFalse(Files.exists(bookPath));
      store
          .lifecycle
          .transactions()
          .transaction()
          .beginAttestedPlan(
              "field-test-plan",
              new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer()));
      assertTrue(store.lifecycle.transactions().transaction().active());
      assertFalse(store.lifecycle.transactions().transaction().begunInDatabase());
      assertFalse(Files.exists(bookPath));
    }

    assertFalse(Files.exists(bookPath));
    assertFalse(Files.exists(bookPath.resolveSibling("book.sqlite-wal")));
    assertFalse(Files.exists(bookPath.resolveSibling("book.sqlite-shm")));
  }

  @Test
  void closingAnActivePlanRetainsMissingBookArtifactsCreatedBeforeTheSessionCloses()
      throws Exception {
    Path bookPath = tempDirectory.resolve("close-active-plan").resolve("book.sqlite");
    try (SqlitePostingFactStore store =
        openStore(bookAccess(bookPath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      store
          .lifecycle
          .transactions()
          .transaction()
          .beginAttestedPlan(
              "close-active-plan",
              new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer()));

      store.lifecycle.database();
      assertTrue(Files.exists(bookPath));

      store.close();
    }

    assertTrue(Files.exists(bookPath));
    assertTrue(Files.isDirectory(Objects.requireNonNull(bookPath.getParent(), "bookPath parent")));

    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "retained plan target", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle laterExclusiveOpen =
          new SqliteStoreLifecycle(
              new SqliteStoreContext(
                  bookPath,
                  SqliteStoreAccessMode.READ_WRITE_CREATE_EXCLUSIVE,
                  SqliteNativeBootstrap::api),
              sessionSecret);

      ContractFailureException failure =
          assertThrows(ContractFailureException.class, laterExclusiveOpen::database);

      assertEquals(
          ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED, failure.failure().descriptor());
      laterExclusiveOpen.close();
    }
  }

  @Test
  void directBookOpening_isRejectedBeforeItCanCreateAPlanOwnedMissingBookPath() throws Exception {
    Path bookPath = tempDirectory.resolve("direct-open-blocked").resolve("book.sqlite");
    try (SqlitePostingFactStore store =
        openStore(bookAccess(bookPath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      AttestationPlanOperationAuthorizer planAuthorizer =
          new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
      store
          .lifecycle
          .transactions()
          .transaction()
          .beginAttestedPlan("direct-open-blocked", planAuthorizer);

      try (RecordingDatabase database = new RecordingDatabase()) {
        assertThrows(
            IllegalStateException.class,
            () -> store.lifecycle.transactions().transaction().beginImmediateIfNeeded(database));
        assertEquals(List.of(), database.statements);
      }

      assertThrows(
          IllegalStateException.class,
          () ->
              store.openAttestedBook(
                  java.time.Instant.parse("2026-07-22T12:00:00Z"),
                  bookIdentity(),
                  List.of(),
                  SqliteAttestationTestSupport.genesis(
                      bookIdentity(), java.time.Instant.parse("2026-07-22T12:00:00Z"))));
      assertFalse(Files.exists(bookPath.getParent()));
      assertFalse(Files.exists(bookPath));

      store.lifecycle.transactions().transaction().rollback();
    }
  }

  @Test
  void planOpeningFailureRetainsArtifactsCreatedBeforeTheDatabaseCouldBePublished()
      throws Exception {
    Path bookPath = tempDirectory.resolve("plan-opening-failure").resolve("book.sqlite");
    IllegalStateException failure =
        new IllegalStateException("forced opening failure after creation");
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "plan opening failure", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(
              artifactCreatingFailureContext(bookPath, failure), sessionSecret);

      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  lifecycle
                      .transactions()
                      .transaction()
                      .beginAttestedPlan(
                          "plan-opening-failure",
                          new AttestationPlanOperationAuthorizer(
                              SqliteAttestationTestSupport.authorizer()))));
      assertFalse(lifecycle.transactions().transaction().active());
      assertEquals("partially-created SQLite artifact", Files.readString(bookPath));
      assertTrue(
          Files.isDirectory(Objects.requireNonNull(bookPath.getParent(), "bookPath parent")));

      lifecycle.close();
    }
  }

  @Test
  void deferredPlanCommitAndOwnedTransactionBoundaryKeepDatabaseAdmissionExplicit()
      throws Exception {
    Path deferredBookPath = tempDirectory.resolve("deferred-commit.sqlite");
    try (SqlitePostingFactStore deferredStore =
        openStore(bookAccess(deferredBookPath), SqliteStoreAccessMode.PLAN_READ_ONLY)) {
      deferredStore
          .lifecycle
          .transactions()
          .transaction()
          .beginReadOnlyPlan("deferred-read-only-plan");
      deferredStore.lifecycle.transactions().transaction().commit();
      assertFalse(deferredStore.lifecycle.transactions().transaction().active());
      assertFalse(Files.exists(deferredBookPath));
    }

    Path existingBookPath = tempDirectory.resolve("owned-boundary.sqlite");
    Files.writeString(existingBookPath, "existing book placeholder");
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters(
                    "owned boundary", TEST_BOOK_KEY.toCharArray()));
        RecordingDatabase database = new RecordingDatabase()) {
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(
              new SqliteStoreContext(
                  existingBookPath,
                  SqliteStoreAccessMode.READ_WRITE_CREATE,
                  SqliteNativeBootstrap::api),
              sessionSecret);
      assertEquals(
          SqliteTransactionOwnership.OWNED,
          lifecycle.transactions().transaction().beginImmediateIfNeeded(database));
      assertEquals(List.of("begin immediate"), database.statements);
      assertFalse(lifecycle.transactions().transaction().active());
      lifecycle.close();
    }
  }

  @Test
  void independentlyComposedTransactionControlSharesTheActiveLifecycleTransaction()
      throws Exception {
    Path bookPath = tempDirectory.resolve("independent-transaction-control.sqlite");
    SqliteStoreContext context =
        new SqliteStoreContext(
            bookPath, SqliteStoreAccessMode.PLAN_READ_ONLY, SqliteNativeBootstrap::api);
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters(
                    "independent transaction control", TEST_BOOK_KEY.toCharArray()));
        RecordingDatabase database = new RecordingDatabase()) {
      SqliteStoreLifecycle lifecycle = new SqliteStoreLifecycle(context, sessionSecret);
      SqliteLedgerPlanTransactionStateHolder transactionState =
          new SqliteLedgerPlanTransactionStateHolder();
      SqliteLedgerPlanTransactionLifecycle transactionLifecycle =
          new SqliteLedgerPlanTransactionLifecycle(context, transactionState);
      SqliteStoreLedgerPlanTransactionControl control =
          new SqliteStoreLedgerPlanTransactionControl(
              lifecycle,
              transactionLifecycle,
              new SqliteLedgerPlanExecution(new SqliteLedgerPlanTransactionStateHolder()));

      transactionLifecycle.beginReadOnlyPlan(
          "independently-composed-plan",
          () -> {
            throw new AssertionError("A missing plan must not open SQLite before admission.");
          });
      assertFalse(control.begunInDatabase());

      assertEquals(SqliteTransactionOwnership.SHARED, control.beginImmediateIfNeeded(database));
      assertEquals(List.of("begin immediate"), database.statements);
      assertTrue(control.begunInDatabase());

      control.rollback();
      assertFalse(control.begunInDatabase());
      lifecycle.close();
    }
  }

  @Test
  void independentlyComposedPlanAdmissionRequiresAnActiveTransactionLifecycle() throws Exception {
    Path bookPath = tempDirectory.resolve("independent-plan-admission.sqlite");
    SqliteStoreContext context =
        new SqliteStoreContext(
            bookPath, SqliteStoreAccessMode.PLAN_EXECUTION, SqliteNativeBootstrap::api);
    AttestationPlanOperationAuthorizer authorizer =
        new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters(
                    "independent plan admission", TEST_BOOK_KEY.toCharArray()));
        RecordingDatabase database = new RecordingDatabase()) {
      SqliteStoreLifecycle lifecycle = new SqliteStoreLifecycle(context, sessionSecret);
      SqliteLedgerPlanTransactionStateHolder executionState =
          new SqliteLedgerPlanTransactionStateHolder();
      SqliteLedgerPlanTransactionLifecycle executionLifecycle =
          new SqliteLedgerPlanTransactionLifecycle(context, executionState);
      SqliteLedgerPlanExecution planExecution = new SqliteLedgerPlanExecution(executionState);
      executionLifecycle.beginAttestedPlan(
          "independent-plan-admission",
          authorizer,
          () -> {
            throw new AssertionError("A missing plan must not open SQLite before admission.");
          });
      planExecution.enterPlanStep(0);
      SqliteStoreLedgerPlanMutationAdmission admission =
          new SqliteStoreLedgerPlanMutationAdmission(
              lifecycle,
              new SqliteLedgerPlanTransactionLifecycle(
                  context, new SqliteLedgerPlanTransactionStateHolder()),
              planExecution);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> admission.admitPlanChildWrite(database, authorizer));
      assertEquals(
          "Plan child mutations require an active aggregate-attested ledger plan.",
          failure.getMessage());
      assertEquals(List.of(), database.statements);

      admission.abortAttestedPlanOnChildFailure(failure);
      assertEquals(List.of(), List.of(failure.getSuppressed()));
      executionLifecycle.rollback(null);

      IllegalStateException outsidePlanFailure = new IllegalStateException("outside active plan");
      admission.abortAttestedPlanOnChildFailure(outsidePlanFailure);
      assertEquals(List.of(), List.of(outsidePlanFailure.getSuppressed()));
      lifecycle.close();
    }
  }

  @Test
  void planAdmissionPreservesTheChildFailureWhileResettingTheTransaction() throws Exception {
    Path bookPath = tempDirectory.resolve("plan-admission-retention.sqlite");
    SqliteStoreContext context =
        new SqliteStoreContext(
            bookPath, SqliteStoreAccessMode.PLAN_EXECUTION, SqliteNativeBootstrap::api);
    AttestationPlanOperationAuthorizer authorizer =
        new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "plan admission retention", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle = new SqliteStoreLifecycle(context, sessionSecret);
      SqliteLedgerPlanTransactionStateHolder sharedState =
          new SqliteLedgerPlanTransactionStateHolder();
      SqliteLedgerPlanTransactionLifecycle transactionLifecycle =
          new SqliteLedgerPlanTransactionLifecycle(context, sharedState);
      SqliteLedgerPlanExecution planExecution = new SqliteLedgerPlanExecution(sharedState);
      transactionLifecycle.beginAttestedPlan(
          "plan-admission-retention",
          authorizer,
          () -> {
            throw new AssertionError("A missing plan must not open SQLite before admission.");
          });
      SqliteStoreLedgerPlanMutationAdmission admission =
          new SqliteStoreLedgerPlanMutationAdmission(
              lifecycle, transactionLifecycle, planExecution);
      IllegalStateException childFailure = new IllegalStateException("child mutation failed");

      admission.abortAttestedPlanOnChildFailure(childFailure);

      assertEquals(List.of(), List.of(childFailure.getSuppressed()));
      assertFalse(transactionLifecycle.active());
      lifecycle.close();
    }
  }

  @Test
  void openingFailures_areRememberedAndPrimeRetainsTheirContractDecision() throws Exception {
    Path missingBookPath = tempDirectory.resolve("missing.sqlite");
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters("missing book", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle missingLifecycle =
          new SqliteStoreLifecycle(
              new SqliteStoreContext(
                  missingBookPath,
                  SqliteStoreAccessMode.PLAN_EXECUTION,
                  SqliteNativeBootstrap::api),
              sessionSecret);
      assertInstanceOf(ContractDecision.Accepted.class, missingLifecycle.prime());
      assertFalse(missingLifecycle.allowsInitializedWorkflow());
      missingLifecycle.close();
    }

    Path callerPath = tempDirectory.resolve("caller-path.sqlite");
    SqliteCallerPathContractException callerPathFailure =
        new SqliteCallerPathContractException(
            callerPath, SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED, "caller path failure");
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters("caller path", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(failingContext(callerPath, callerPathFailure), sessionSecret);
      assertEquals(
          ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH,
          lifecycle.prime().requireRejected().descriptor());
      dev.erst.fingrind.contract.runtime.ContractFailureException firstFailure =
          assertThrows(
              dev.erst.fingrind.contract.runtime.ContractFailureException.class,
              lifecycle::database);
      assertSame(
          firstFailure,
          assertThrows(
              dev.erst.fingrind.contract.runtime.ContractFailureException.class,
              lifecycle::database));
      lifecycle.close();
    }

    Path runtimePath = tempDirectory.resolve("runtime-path.sqlite");
    IllegalStateException runtimeFailure = new IllegalStateException("opening runtime failure");
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters("runtime path", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(failingContext(runtimePath, runtimeFailure), sessionSecret);
      assertSame(runtimeFailure, assertThrows(IllegalStateException.class, lifecycle::database));
      lifecycle.close();
    }
  }

  @Test
  void initializedBookGate_preservesTheCanonicalNonCurrentFormatFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("unsupported-format.sqlite");
    int detectedFormatVersion = SqliteBookContract.FORMAT_VERSION + 1;
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters(
                    "unsupported format", TEST_BOOK_KEY.toCharArray()));
        RecordingDatabase database = new RecordingDatabase()) {
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(
              new SqliteStoreContext(
                  bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api),
              sessionSecret) {
            @Override
            SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
              return new SqliteBookStateSnapshot(
                  SqliteBookContract.APPLICATION_ID,
                  detectedFormatVersion,
                  SqliteBookState.UNSUPPORTED_FINGRIND_VERSION);
            }
          };

      ContractFailureException failure =
          assertThrows(ContractFailureException.class, () -> lifecycle.isInitializedBook(database));

      assertEquals("unsupported-book-format-version", failure.failure().code());
      ContractFailureDetails.UnsupportedBookFormatVersion details =
          assertInstanceOf(
              ContractFailureDetails.UnsupportedBookFormatVersion.class,
              failure.failure().details());
      assertEquals(detectedFormatVersion, details.detectedBookFormatVersion());
      assertEquals(SqliteBookContract.FORMAT_VERSION, details.supportedBookFormatVersion());
      lifecycle.close();
    }
  }

  private static SqliteStoreContext failingContext(Path bookPath, RuntimeException failure) {
    return new SqliteStoreContext(
        bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api) {
      @Override
      SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
        throw failure;
      }
    };
  }

  private static SqliteStoreContext artifactCreatingFailureContext(
      Path bookPath, RuntimeException failure) {
    return new SqliteStoreContext(
        bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api) {
      @Override
      SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
        try {
          Files.writeString(bookPath(), "partially-created SQLite artifact");
        } catch (java.io.IOException exception) {
          throw new java.io.UncheckedIOException(exception);
        }
        throw failure;
      }
    };
  }

  /** Records transaction-control calls made through lifecycle ownership. */
  private static final class RecordingDatabase extends SqliteNativeDatabase {
    private final List<String> statements = new ArrayList<>();

    private RecordingDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    void executeStatement(String sql) {
      statements.add(sql);
    }

    @Override
    public void close() {}
  }
}
