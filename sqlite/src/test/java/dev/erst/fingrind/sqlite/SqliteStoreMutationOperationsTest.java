package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for isolated recovery helpers inside {@link SqliteStoreMutationOperations}. */
@NullUnmarked
class SqliteStoreMutationOperationsTest {
  @TempDir Path tempDirectory;

  @Test
  void captureBestEffortRuntimeFailure_returnsNullOnSuccessAndReturnsRuntimeFailure() {
    assertNull(SqliteStoreMutationOperations.captureBestEffortRuntimeFailure(() -> {}));

    RuntimeException failure = new IllegalStateException("close failed");
    assertSame(
        failure,
        SqliteStoreMutationOperations.captureBestEffortRuntimeFailure(
            () -> {
              throw failure;
            }));
  }

  @Test
  void catastrophicRekeyRestoreFailure_preservesVerificationAndSuppressedFailures() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException restoreFailure = new IllegalStateException("restore failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");

    IllegalStateException failure =
        SqliteStoreMutationOperations.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, closeFailure);

    assertSame(verificationFailure, failure.getCause());
    assertEquals(2, failure.getSuppressed().length);
    assertSame(restoreFailure, failure.getSuppressed()[0]);
    assertSame(closeFailure, failure.getSuppressed()[1]);

    IllegalStateException withoutCloseFailure =
        SqliteStoreMutationOperations.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, null);
    assertEquals(1, withoutCloseFailure.getSuppressed().length);
    assertSame(restoreFailure, withoutCloseFailure.getSuppressed()[0]);
  }

  @Test
  void restoredOriginalBookFailure_preservesVerificationCauseAndOptionalCloseFailure() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");

    IllegalStateException withCloseFailure =
        SqliteStoreMutationOperations.restoredOriginalBookFailure(
            verificationFailure, closeFailure);
    assertSame(verificationFailure, withCloseFailure.getCause());
    assertEquals(1, verificationFailure.getSuppressed().length);
    assertSame(closeFailure, verificationFailure.getSuppressed()[0]);

    RuntimeException cleanVerificationFailure = new IllegalStateException("verify failed cleanly");
    IllegalStateException withoutCloseFailure =
        SqliteStoreMutationOperations.restoredOriginalBookFailure(cleanVerificationFailure, null);
    assertSame(cleanVerificationFailure, withoutCloseFailure.getCause());
    assertEquals(0, cleanVerificationFailure.getSuppressed().length);
  }

  @Test
  void finalizeFailedRekey_selectsTheCorrectFailureShapeAndDeletesOnlyAfterSuccessfulRestore() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException restoreFailure = new IllegalStateException("restore failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");
    AtomicInteger deleteCalls = new AtomicInteger();

    IllegalStateException catastrophic =
        SqliteStoreMutationOperations.finalizeFailedRekey(
            verificationFailure, restoreFailure, closeFailure, deleteCalls::incrementAndGet);
    assertSame(verificationFailure, catastrophic.getCause());
    assertEquals(2, catastrophic.getSuppressed().length);
    assertEquals(0, deleteCalls.get());

    RuntimeException restoredVerificationFailure =
        new IllegalStateException("verify failed cleanly");
    IllegalStateException restored =
        SqliteStoreMutationOperations.finalizeFailedRekey(
            restoredVerificationFailure, null, closeFailure, deleteCalls::incrementAndGet);
    assertSame(restoredVerificationFailure, restored.getCause());
    assertEquals(1, restoredVerificationFailure.getSuppressed().length);
    assertEquals(1, deleteCalls.get());
  }

  @Test
  void publishRekeyedDatabase_closesReplacementHandleWhenPublicationFailsBeforeSwap() {
    Path bookPath = tempDirectory.resolve("publish-rekeyed-database.sqlite");
    CapturingStoreContext store =
        new CapturingStoreContext(
            bookPath,
            SqliteBookPassphrase.fromCharacters(
                "store mutation test book", "book-key".toCharArray()));

    try (store;
        SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation test replacement", "replacement-key".toCharArray())) {
      SqliteStoreMutationOperations operations = new SqliteStoreMutationOperations(store);
      SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
      SqliteBookSchemaBootstrap.initializeBook(store.database());
      SqliteMutationWriter.insertInitializedAt(
          store.database(), Instant.parse("2026-04-29T10:15:30Z"));
      SqliteNativeConnections.rekey(store.database(), replacementPassphrase);
      SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);
      try {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    operations.publishRekeyedDatabase(
                        store.database(), replacementPassphrase, rollbackFile));
        assertEquals("forced validation failure", failure.getMessage());
        assertNotNull(store.reopenedDatabase());
        IllegalStateException closedHandleFailure =
            assertThrows(IllegalStateException.class, () -> store.reopenedDatabase().handle());
        assertEquals(
            "SQLite native database handle is already closed.", closedHandleFailure.getMessage());
      } finally {
        rollbackFile.deleteQuietly();
      }
    }
  }

  /** Test-only store seam that captures the reopened replacement handle before publication. */
  private static final class CapturingStoreContext extends SqliteStoreContext {
    private SqliteNativeDatabase reopenedDatabase;

    CapturingStoreContext(Path bookPath, SqliteBookPassphrase bookPassphrase) {
      super(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
    }

    @Override
    void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
      if (activeDatabase.equals(reopenedDatabase)) {
        throw new IllegalStateException("forced validation failure");
      }
      super.requireInitializedBook(activeDatabase);
    }

    @Override
    SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
      reopenedDatabase = super.openConfiguredDatabase(bookPassphrase);
      return reopenedDatabase;
    }

    SqliteNativeDatabase reopenedDatabase() {
      return reopenedDatabase;
    }
  }
}
