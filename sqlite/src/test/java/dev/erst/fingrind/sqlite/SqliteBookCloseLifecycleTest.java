package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.IdempotencyKey;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookCloseLifecycleTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void close_failureMarksSessionClosedAndRequiresFreshHandleOwnerForCleanup() throws Exception {
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
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to close SQLite book connection."));
      assertNull(storeDatabase(postingFactStore));
      assertTrue(storeBooleanField(postingFactStore, "closed"));
      assertDoesNotThrow(() -> new SqliteNativeDatabase(activeHandle).close());
    }
  }

  @Test
  void closeReopenedDatabaseQuietly_toleratesNullAndNativeCloseFailures() {
    try (ClosingSqliteNativeDatabase closingDatabase = new ClosingSqliteNativeDatabase();
        ThrowingSqliteNativeDatabase database = new ThrowingSqliteNativeDatabase()) {
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(closingDatabase));
      assertTrue(closingDatabase.closeAttempted());
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(closingDatabase));
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(database));
      assertTrue(database.closeAttempted());
      assertDoesNotThrow(
          () -> SqliteStoreOperations.closeReopenedDatabaseQuietly((SqliteNativeDatabase) null));
    }
  }

  @Test
  void lifecycleFailureFactories_returnOperatorFacingMessages() {
    assertEquals(
        "The selected SQLite file is not a FinGrind book.",
        SqliteStoreOperations.foreignBookFailure().getMessage());
    assertEquals(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.",
        SqliteStoreOperations.incompleteBookFailure().getMessage());
    assertEquals(
        "The selected FinGrind book format version 7 is unsupported. Expected version 3.",
        SqliteStoreOperations.unsupportedBookVersionFailure(7, 3).getMessage());
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
    assertFalse(NullTestSupport.messageOf(exception).isBlank());
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
    assertDoesNotThrow(
        () ->
            SqliteConnectionConfigurer.closeAfterConfigurationFailure(
                staleDatabaseHandle(tempDirectory.resolve("stale-close.sqlite")),
                (action, exception) ->
                    cleanupReports.add(action + "|" + exception.getClass().getSimpleName())));
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
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to close SQLite book connection."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void close_wrapsLifecycleCloseFailureAndMarksSessionTerminal() {
    Path bookPath = tempDirectory.resolve("close-illegal-state-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, new IllegalStateClosingSqliteNativeDatabase());
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::close);
      assertEquals("Failed to close SQLite book connection.", exception.getMessage());
      assertEquals(
          "Simulated lifecycle close failure.",
          NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
      assertNull(storeDatabase(postingFactStore));
      assertTrue(storeBooleanField(postingFactStore, "closed"));
    }
  }
}
