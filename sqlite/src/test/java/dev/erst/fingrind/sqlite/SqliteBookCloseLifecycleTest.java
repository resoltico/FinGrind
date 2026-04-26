package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.IdempotencyKey;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookCloseLifecycleTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void close_retainsDatabaseHandleUntilCloseEventuallySucceeds() throws Exception {
    Path databasePath = tempDirectory.resolve("close-retry.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      MemorySegment activeHandle = requireStoreDatabase(postingFactStore).handle();
      setStoreDatabase(
          postingFactStore,
          new SqliteNativeDatabase(
              activeHandle,
              SqliteNativeApiTestSupport.withCloseV2(
                  SqliteNativeBootstrap.api(), constantMethodHandle(14, MemorySegment.class))));

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::close);

      assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
      assertSame(activeHandle, requireStoreDatabase(postingFactStore).handle());
      assertFalse(storeBooleanField(postingFactStore, "closed"));

      setStoreDatabase(postingFactStore, new SqliteNativeDatabase(activeHandle));

      assertDoesNotThrow(postingFactStore::close);
      assertNull(storeDatabase(postingFactStore));
      assertTrue(storeBooleanField(postingFactStore, "closed"));
    }
  }

  @Test
  void closeReopenedDatabaseQuietly_toleratesNullAndNativeCloseFailures() {
    try (ClosingSqliteNativeDatabase closingDatabase = new ClosingSqliteNativeDatabase();
        ThrowingSqliteNativeDatabase database = new ThrowingSqliteNativeDatabase()) {
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(closingDatabase));
      assertTrue(closingDatabase.closeAttempted());
      assertDoesNotThrow(
          () ->
              SqliteStoreOperations.closeReopenedDatabaseQuietly(
                  new SqliteSessionDatabase(closingDatabase)));
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(database));
      assertTrue(database.closeAttempted());
      assertDoesNotThrow(
          () -> SqliteStoreOperations.closeReopenedDatabaseQuietly((SqliteNativeDatabase) null));
    }
  }

  @Test
  void configureOpenedDatabase_closesUnconfiguredDatabaseQuietlyWhenPragmasFail() throws Exception {
    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteConnectionConfigurer.configureOpenedDatabase(
                    staleDatabaseHandle(tempDirectory.resolve("stale.sqlite")),
                    SqliteStoreAccessMode.READ_WRITE_CREATE));

    assertFalse(exception.getMessage().isBlank());
  }

  @Test
  void closeAfterConfigurationFailure_closesOpenDatabase() throws Exception {
    Path bookPath = tempDirectory.resolve("configured-close.sqlite");
    try (SqliteNativeDatabase database = SqliteNativeConnections.open(bookAccess(bookPath))) {
      assertDoesNotThrow(() -> SqliteConnectionConfigurer.closeAfterConfigurationFailure(database));
    }
  }

  @Test
  void closeAfterConfigurationFailure_reportsNativeCloseFailureWithoutThrowing() throws Exception {
    List<String> cleanupReports = new ArrayList<>();

    try (SqliteBestEffort.ReporterOverride ignored =
        SqliteBestEffort.replaceReporterForTesting(
            (action, exception) ->
                cleanupReports.add(action + "|" + exception.getClass().getSimpleName()))) {
      assertDoesNotThrow(
          () ->
              SqliteConnectionConfigurer.closeAfterConfigurationFailure(
                  staleDatabaseHandle(tempDirectory.resolve("stale-close.sqlite"))));
    }

    assertEquals(
        List.of("closing one SQLite database after configuration failure|SqliteNativeException"),
        cleanupReports);
  }

  @Test
  void close_rejectsFurtherUse() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("closed.sqlite")))) {
      postingFactStore.close();

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-closed")));

      assertEquals("SQLite book session is already closed.", exception.getMessage());
    }
  }

  @Test
  void close_wrapsNativeDatabaseCloseFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::close);

      assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
      setStoreDatabase(postingFactStore, null);
    }
  }
}
