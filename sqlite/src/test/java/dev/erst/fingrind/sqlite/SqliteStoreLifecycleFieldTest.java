package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises lifecycle-level plan deferral, cleanup, and deterministic opening failures. */
class SqliteStoreLifecycleFieldTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void planTransaction_defersMissingBookCreationAndCleansArtifactsWhenTheSessionCloses()
      throws Exception {
    Path bookPath = tempDirectory.resolve("plan").resolve("book.sqlite");
    try (SqlitePostingFactStore store =
        openStore(bookAccess(bookPath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      assertFalse(Files.exists(bookPath));
      store.lifecycle.transactions().begin();
      assertTrue(store.lifecycle.transactions().active());
      assertFalse(store.lifecycle.transactions().begunInDatabase());
      assertFalse(Files.exists(bookPath));
      store.lifecycle.database();
      assertTrue(store.lifecycle.transactions().begunInDatabase());
      assertTrue(Files.isRegularFile(bookPath));
    }

    assertFalse(Files.exists(bookPath));
    assertFalse(Files.exists(bookPath.resolveSibling("book.sqlite-wal")));
    assertFalse(Files.exists(bookPath.resolveSibling("book.sqlite-shm")));
  }

  @Test
  void deferredPlanCommitAndOwnedTransactionBoundaryKeepDatabaseAdmissionExplicit()
      throws Exception {
    Path deferredBookPath = tempDirectory.resolve("deferred-commit.sqlite");
    try (SqlitePostingFactStore deferredStore =
        openStore(bookAccess(deferredBookPath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      deferredStore.lifecycle.transactions().begin();
      deferredStore.lifecycle.transactions().commit();
      assertFalse(deferredStore.lifecycle.transactions().active());
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
          lifecycle.transactions().beginImmediateIfNeeded(database));
      assertEquals(List.of("begin immediate"), database.statements);
      assertFalse(lifecycle.transactions().active());
      lifecycle.transactions().cleanupCreatedMissingBookArtifactsIfPresent();
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

  private static SqliteStoreContext failingContext(Path bookPath, RuntimeException failure) {
    return new SqliteStoreContext(
        bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api) {
      @Override
      SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
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
