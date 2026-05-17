package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for isolated recovery helpers inside {@link SqliteRekeyService}. */
class SqliteStoreMutationOperationsTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void captureBestEffortRuntimeFailure_returnsNullOnSuccessAndReturnsRuntimeFailure() {
    assertNull(SqliteRekeyService.captureBestEffortRuntimeFailure(() -> {}));
    RuntimeException failure = new IllegalStateException("close failed");
    assertSame(
        failure,
        SqliteRekeyService.captureBestEffortRuntimeFailure(
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
        SqliteRekeyService.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, closeFailure);
    assertSame(verificationFailure, failure.getCause());
    assertEquals(2, failure.getSuppressed().length);
    assertSame(restoreFailure, failure.getSuppressed()[0]);
    assertSame(closeFailure, failure.getSuppressed()[1]);
    IllegalStateException withoutCloseFailure =
        SqliteRekeyService.catastrophicRekeyRestoreFailure(
            verificationFailure, restoreFailure, null);
    assertEquals(1, withoutCloseFailure.getSuppressed().length);
    assertSame(restoreFailure, withoutCloseFailure.getSuppressed()[0]);
  }

  @Test
  void restoredOriginalBookFailure_preservesVerificationCauseAndOptionalCloseFailure() {
    RuntimeException verificationFailure = new IllegalStateException("verify failed");
    RuntimeException closeFailure = new IllegalStateException("close failed");
    IllegalStateException withCloseFailure =
        SqliteRekeyService.restoredOriginalBookFailure(verificationFailure, closeFailure);
    assertSame(verificationFailure, withCloseFailure.getCause());
    assertEquals(1, verificationFailure.getSuppressed().length);
    assertSame(closeFailure, verificationFailure.getSuppressed()[0]);
    RuntimeException cleanVerificationFailure = new IllegalStateException("verify failed cleanly");
    IllegalStateException withoutCloseFailure =
        SqliteRekeyService.restoredOriginalBookFailure(cleanVerificationFailure, null);
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
        SqliteRekeyService.finalizeFailedRekey(
            verificationFailure, restoreFailure, closeFailure, deleteCalls::incrementAndGet);
    assertSame(verificationFailure, catastrophic.getCause());
    assertEquals(2, catastrophic.getSuppressed().length);
    assertEquals(0, deleteCalls.get());
    RuntimeException restoredVerificationFailure =
        new IllegalStateException("verify failed cleanly");
    IllegalStateException restored =
        SqliteRekeyService.finalizeFailedRekey(
            restoredVerificationFailure, null, closeFailure, deleteCalls::incrementAndGet);
    assertSame(restoredVerificationFailure, restored.getCause());
    assertEquals(1, restoredVerificationFailure.getSuppressed().length);
    assertEquals(1, deleteCalls.get());
  }

  @Test
  void publishRekeyedDatabase_closesReplacementHandleWhenPublicationFailsBeforeSwap() {
    Path bookPath = tempDirectory.resolve("publish-rekeyed-database.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation test book", "book-key".toCharArray());
        SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation test replacement", "replacement-key".toCharArray())) {
      CapturingStoreContext context =
          new CapturingStoreContext(bookPath, SqliteNativeBootstrap::api);
      CapturingStoreLifecycle lifecycle = new CapturingStoreLifecycle(context, bookPassphrase);
      SqliteRekeyService rekeyService = new SqliteRekeyService(context, lifecycle);
      SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
      SqliteBookSchemaBootstrap.initializeBook(lifecycle.database());
      SqliteBookIntegrityVerifier.recordSchemaFingerprint(lifecycle.database());
      SqliteMutationWriter.insertInitializedAt(
          lifecycle.database(), Instant.parse("2026-04-29T10:15:30Z"));
      SqliteMutationWriter.insertBookIdentity(
          lifecycle.database(), SqlitePostingFactFixtureSupport.bookIdentity());
      SqliteAuditEventWriter.insertAuditEvent(
          lifecycle.database(),
          dev.erst.fingrind.executor.bookkeeping.BookAuditEvent.bookOpened(
              Instant.parse("2026-04-29T10:15:30Z")));
      SqliteNativeConnections.rekey(lifecycle.database(), replacementPassphrase);
      SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);
      try {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    rekeyService.publishRekeyedDatabase(
                        lifecycle.database(),
                        replacementPassphrase,
                        rollbackFile,
                        Instant.parse("2026-04-08T10:15:30Z")));
        assertEquals("forced validation failure", failure.getMessage());
        assertNotNull(context.reopenedDatabase());
        IllegalStateException closedHandleFailure =
            assertThrows(IllegalStateException.class, () -> context.reopenedDatabase().handle());
        assertEquals(
            "SQLite native database handle is already closed.", closedHandleFailure.getMessage());
      } finally {
        rollbackFile.deleteQuietly();
        assertDoesNotThrow(lifecycle::close);
      }
    }
  }

  @Test
  void publishRekeyedDatabase_rollsBackAuditInsertionFailuresBeforePublication() {
    Path bookPath = tempDirectory.resolve("publish-rekeyed-database-audit-failure.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation audit failure book", "book-key".toCharArray());
        SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation audit failure replacement", "replacement-key".toCharArray())) {
      AuditFailingStoreContext context =
          new AuditFailingStoreContext(bookPath, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, new SqliteSessionSecret(bookPassphrase));
      SqliteRekeyService rekeyService = new SqliteRekeyService(context, lifecycle);
      SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
      SqliteBookSchemaBootstrap.initializeBook(lifecycle.database());
      SqliteBookIntegrityVerifier.recordSchemaFingerprint(lifecycle.database());
      SqliteMutationWriter.insertInitializedAt(
          lifecycle.database(), Instant.parse("2026-04-29T10:15:30Z"));
      SqliteMutationWriter.insertBookIdentity(
          lifecycle.database(), SqlitePostingFactFixtureSupport.bookIdentity());
      SqliteAuditEventWriter.insertAuditEvent(
          lifecycle.database(),
          dev.erst.fingrind.executor.bookkeeping.BookAuditEvent.bookOpened(
              Instant.parse("2026-04-29T10:15:30Z")));
      SqliteNativeConnections.rekey(lifecycle.database(), replacementPassphrase);
      SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);
      try {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    rekeyService.publishRekeyedDatabase(
                        lifecycle.database(),
                        replacementPassphrase,
                        rollbackFile,
                        Instant.parse("2026-04-08T10:15:30Z")));
        assertEquals("forced audit insert failure", failure.getMessage());
        assertEquals(
            0,
            SqliteStatementQueries.querySingleInt(
                lifecycle.database(),
                "select count(*) from audit_event where event_kind = 'BOOK_REKEYED'"));
        assertNotNull(context.reopenedDatabase());
        IllegalStateException closedHandleFailure =
            assertThrows(IllegalStateException.class, () -> context.reopenedDatabase().handle());
        assertEquals(
            "SQLite native database handle is already closed.", closedHandleFailure.getMessage());
      } finally {
        rollbackFile.deleteQuietly();
        assertDoesNotThrow(lifecycle::close);
      }
    }
  }

  @Test
  void rekeyBook_delegatesThroughMutationOperationsAndPublishesTheReplacementKey() {
    Path bookPath = tempDirectory.resolve("store-mutation-rekey.sqlite");
    Instant initializedAt = Instant.parse("2026-04-29T10:15:30Z");
    Instant rekeyedAt = Instant.parse("2026-04-30T10:15:30Z");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation rekey original", "book-key".toCharArray());
        SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "store mutation rekey replacement", "replacement-key".toCharArray())) {
      SqliteStoreContext context =
          new SqliteStoreContext(
              bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, SqliteNativeBootstrap::api);
      SqliteStoreLifecycle lifecycle =
          new SqliteStoreLifecycle(context, new SqliteSessionSecret(bookPassphrase));
      SqliteStoreMutationOperations mutationOperations =
          new SqliteStoreMutationOperations(context, lifecycle);
      try {
        SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
        SqliteBookSchemaBootstrap.initializeBook(lifecycle.database());
        SqliteBookIntegrityVerifier.recordSchemaFingerprint(lifecycle.database());
        SqliteMutationWriter.insertInitializedAt(lifecycle.database(), initializedAt);
        SqliteMutationWriter.insertBookIdentity(
            lifecycle.database(), SqlitePostingFactFixtureSupport.bookIdentity());
        SqliteAuditEventWriter.insertAuditEvent(
            lifecycle.database(),
            dev.erst.fingrind.executor.bookkeeping.BookAuditEvent.bookOpened(initializedAt));

        assertEquals(
            new RekeyBookResult.Rekeyed(bookPath.toAbsolutePath().normalize()),
            mutationOperations.rekeyBook(replacementPassphrase, rekeyedAt));
        assertDoesNotThrow(lifecycle.database()::handle);
      } finally {
        assertDoesNotThrow(lifecycle::close);
      }
      try (SqliteBookPassphrase reopenedReplacementPassphrase =
              SqliteBookPassphrase.fromCharacters(
                  "store mutation rekey replacement reopen", "replacement-key".toCharArray());
          SqliteNativeDatabase reopenedDatabase =
              context.openConfiguredDatabase(reopenedReplacementPassphrase)) {
        assertDoesNotThrow(reopenedDatabase::handle);
      }
    }
  }

  /** Test-only context seam that captures the reopened replacement handle before publication. */
  private static final class CapturingStoreContext extends SqliteStoreContext {
    private @Nullable SqliteNativeDatabase reopenedDatabase;

    CapturingStoreContext(
        Path bookPath, java.util.function.Supplier<SqliteNativeApi> sqliteApiSupplier) {
      super(bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, sqliteApiSupplier);
    }

    @Override
    SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
      reopenedDatabase = super.openConfiguredDatabase(bookPassphrase);
      return reopenedDatabase;
    }

    @Override
    SqliteNativeDatabase openConfiguredDatabaseWithoutRollbackArtifactWarning(
        SqliteBookPassphrase bookPassphrase) {
      reopenedDatabase = super.openConfiguredDatabaseWithoutRollbackArtifactWarning(bookPassphrase);
      return reopenedDatabase;
    }

    SqliteNativeDatabase reopenedDatabase() {
      return Objects.requireNonNull(reopenedDatabase, "reopenedDatabase");
    }
  }

  /** Test-only context seam that forces the rekey audit insert to fail after reopen. */
  private static final class AuditFailingStoreContext extends SqliteStoreContext {
    private int openCount;
    private @Nullable SqliteNativeDatabase reopenedDatabase;

    AuditFailingStoreContext(
        Path bookPath, java.util.function.Supplier<SqliteNativeApi> sqliteApiSupplier) {
      super(bookPath, SqliteStoreAccessMode.READ_WRITE_CREATE, sqliteApiSupplier);
    }

    @Override
    SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
      SqliteNativeDatabase delegate = super.openConfiguredDatabase(bookPassphrase);
      openCount++;
      if (openCount == 1) {
        return delegate;
      }
      reopenedDatabase =
          new SqliteStatementRedirectingDatabase(
              delegate,
              sql -> {
                if (SqlitePostingSql.INSERT_AUDIT_EVENT.equals(sql)) {
                  throw new IllegalStateException("forced audit insert failure");
                }
                return delegate.prepare(sql);
              },
              delegate::close);
      return reopenedDatabase;
    }

    @Override
    SqliteNativeDatabase openConfiguredDatabaseWithoutRollbackArtifactWarning(
        SqliteBookPassphrase bookPassphrase) {
      SqliteNativeDatabase delegate =
          super.openConfiguredDatabaseWithoutRollbackArtifactWarning(bookPassphrase);
      openCount++;
      if (openCount == 1) {
        return delegate;
      }
      reopenedDatabase =
          new SqliteStatementRedirectingDatabase(
              delegate,
              sql -> {
                if (SqlitePostingSql.INSERT_AUDIT_EVENT.equals(sql)) {
                  throw new IllegalStateException("forced audit insert failure");
                }
                return delegate.prepare(sql);
              },
              delegate::close);
      return reopenedDatabase;
    }

    SqliteNativeDatabase reopenedDatabase() {
      return Objects.requireNonNull(reopenedDatabase, "reopenedDatabase");
    }
  }

  /** Test-only lifecycle seam that forces validation failure on the reopened replacement handle. */
  private static final class CapturingStoreLifecycle extends SqliteStoreLifecycle {
    private final CapturingStoreContext context;

    CapturingStoreLifecycle(CapturingStoreContext context, SqliteBookPassphrase bookPassphrase) {
      super(context, new SqliteSessionSecret(bookPassphrase));
      this.context = context;
    }

    @Override
    void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
      if (activeDatabase.equals(context.reopenedDatabase())) {
        throw new IllegalStateException("forced validation failure");
      }
      super.requireInitializedBook(activeDatabase);
    }
  }
}
