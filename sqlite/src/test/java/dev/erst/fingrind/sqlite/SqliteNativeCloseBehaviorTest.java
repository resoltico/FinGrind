package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeCloseBehaviorTest extends SqliteNativeBridgeTestSupport {
  private static final VarHandle ACTIVE_CONNECTIONS =
      staticVarHandle("ACTIVE_CONNECTIONS", AtomicInteger.class);
  private static final VarHandle ACTIVE_CONNECTIONS_BY_BOOK_PATH =
      staticVarHandle("ACTIVE_CONNECTIONS_BY_BOOK_PATH", ConcurrentMap.class);
  private static final VarHandle MARKER_CONNECTIONS_BY_BOOK_PATH =
      staticVarHandle("MARKER_CONNECTIONS_BY_BOOK_PATH", ConcurrentMap.class);
  private static final MethodHandle ROLLBACK_OPENING_CONNECTION =
      bootstrapHelper(
          "rollbackOpeningConnection",
          MethodType.methodType(void.class, Path.class, AtomicInteger.class, AtomicInteger.class));

  @Test
  void close_rethrowsSqliteNativeExceptionFromNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("failThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "close native failure", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      SqliteNativeException exception = assertThrows(SqliteNativeException.class, database::close);
      assertEquals("SQLITE_CANTOPEN", exception.resultName());
    }
  }

  @Test
  void close_keepsActiveConnectionCountUntilSuccessfulRetry() throws Exception {
    Path bookPath = tempDirectory.resolve("close-active-count.sqlite");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    Path activityMarkerPath = activityMarkerPath(normalizedBookPath);
    int initialActiveConnections = SqliteNativeBootstrap.activeConnectionCount();
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("failThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close active count", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertEquals(1, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
      assertTrue(Files.exists(activityMarkerPath));
      assertThrows(SqliteNativeException.class, database::close);
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertEquals(1, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
      assertTrue(Files.exists(activityMarkerPath));
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertEquals(1, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
    }
    assertEquals(initialActiveConnections, SqliteNativeBootstrap.activeConnectionCount());
    assertEquals(0, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
    assertFalse(Files.exists(activityMarkerPath));
  }

  @Test
  void close_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-throwable.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi =
        closeBehaviorApi("throwIllegalStateThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close throwable", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, database::close);
      assertEquals("Failed to close the SQLite native library bridge.", exception.getMessage());
      assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
    }
  }

  @Test
  void readOnlyOpen_tracksActiveConnectionsWithoutPublishingActivityMarker() throws Exception {
    Path bookPath = tempDirectory.resolve("read-only-no-marker.sqlite");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    Path activityMarkerPath = activityMarkerPath(normalizedBookPath);

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "create read-only marker fixture", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase ignored =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeBootstrap.api())) {}

    int initialActiveConnections = SqliteNativeBootstrap.activeConnectionCount();
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "read-only no marker", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase ignored =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_ONLY,
                SqliteNativeBootstrap.api())) {
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertEquals(1, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
      assertFalse(Files.exists(activityMarkerPath));
    }

    assertEquals(initialActiveConnections, SqliteNativeBootstrap.activeConnectionCount());
    assertEquals(0, SqliteNativeBootstrap.activeConnectionCount(normalizedBookPath));
    assertFalse(Files.exists(activityMarkerPath));
  }

  @Test
  void close_rethrowsErrorsFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-error.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("throwAssertionThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close error", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      AssertionError error = assertThrows(AssertionError.class, database::close);
      assertEquals("boom", error.getMessage());
    }
  }

  @Test
  void recordConnectionClosed_rejectsCounterUnderflow() {
    assertEquals(0, SqliteNativeBootstrap.activeConnectionCount());
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> SqliteNativeBootstrap.recordConnectionClosed(null));
    assertEquals("SQLite active connection count underflow.", exception.getMessage());
    assertEquals(0, SqliteNativeBootstrap.activeConnectionCount());
  }

  @Test
  void recordConnectionClosed_rejectsMissingBookRegistryEntries() {
    Path openedBookPath = tempDirectory.resolve("opened.sqlite").toAbsolutePath().normalize();
    Path missingEntryPath =
        tempDirectory.resolve("missing-entry.sqlite").toAbsolutePath().normalize();
    int initialActiveConnections = SqliteNativeBootstrap.activeConnectionCount();

    SqliteNativeBootstrap.recordOpeningConnection(openedBookPath);
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeBootstrap.recordConnectionClosed(missingEntryPath));
      assertEquals(
          "SQLite active connection registry missing the normalized book path entry for "
              + missingEntryPath
              + ".",
          exception.getMessage());
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertEquals(1, SqliteNativeBootstrap.activeConnectionCount(openedBookPath));
    } finally {
      SqliteNativeBootstrap.recordConnectionClosed(openedBookPath);
    }
  }

  @Test
  void recordConnectionClosed_rejectsPerBookCounterUnderflow() {
    Path bookPath = tempDirectory.resolve("per-book-underflow.sqlite").toAbsolutePath().normalize();
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    int baselineConnections = activeConnections.get();

    SqliteNativeBootstrap.recordOpeningConnection(bookPath);
    try {
      activeConnectionsByBookPath.put(bookPath, new AtomicInteger());

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeBootstrap.recordConnectionClosed(bookPath));

      assertEquals(
          "SQLite active connection count underflow for normalized book path " + bookPath + ".",
          exception.getMessage());
      assertEquals(baselineConnections + 1, activeConnections.get());
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
    }
  }

  @Test
  void recordConnectionClosed_rejectsMissingMarkerRegistryEntries() {
    Path bookPath =
        tempDirectory.resolve("missing-marker-entry.sqlite").toAbsolutePath().normalize();
    Path markerPath = activityMarkerPath(bookPath);
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath = markerConnectionsByBookPath();
    int baselineConnections = activeConnections.get();

    SqliteNativeBootstrap.recordOpeningConnection(bookPath);
    try {
      markerConnectionsByBookPath.remove(bookPath);

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeBootstrap.recordConnectionClosed(bookPath));

      assertEquals(
          "SQLite activity-marker registry missing the normalized book path entry for "
              + bookPath
              + ".",
          exception.getMessage());
      assertEquals(baselineConnections + 1, activeConnections.get());
      assertEquals(
          1, java.util.Objects.requireNonNull(activeConnectionsByBookPath.get(bookPath)).get());
      assertTrue(Files.exists(markerPath));
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
      markerConnectionsByBookPath.remove(bookPath);
      SqliteBookActivityMarkers.deleteCurrentProcessMarker(bookPath);
    }
  }

  @Test
  void recordConnectionClosed_rejectsMarkerCounterUnderflow() {
    Path bookPath = tempDirectory.resolve("marker-underflow.sqlite").toAbsolutePath().normalize();
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath = markerConnectionsByBookPath();
    int baselineConnections = activeConnections.get();

    SqliteNativeBootstrap.recordOpeningConnection(bookPath);
    try {
      markerConnectionsByBookPath.put(bookPath, new AtomicInteger());

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeBootstrap.recordConnectionClosed(bookPath));

      assertEquals(
          "SQLite activity-marker connection count underflow for normalized book path "
              + bookPath
              + ".",
          exception.getMessage());
      assertEquals(baselineConnections + 1, activeConnections.get());
      assertEquals(
          1, java.util.Objects.requireNonNull(activeConnectionsByBookPath.get(bookPath)).get());
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
      markerConnectionsByBookPath.remove(bookPath);
      SqliteBookActivityMarkers.deleteCurrentProcessMarker(bookPath);
    }
  }

  @Test
  void recordOpeningConnection_rollsBackCountersWhenMarkerPublicationFails() throws Exception {
    Path bookPath = tempDirectory.resolve("marker-rollback.sqlite").toAbsolutePath().normalize();
    Path markerPath = activityMarkerPath(bookPath);
    Files.createDirectories(markerPath);
    Files.writeString(markerPath.resolve("child.txt"), "child");

    int baselineConnections = SqliteNativeBootstrap.activeConnectionCount();
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeBootstrap.recordOpeningConnection(bookPath));

    assertEquals(
        "Failed to publish one FinGrind SQLite book activity marker.", exception.getMessage());
    assertEquals(baselineConnections, SqliteNativeBootstrap.activeConnectionCount());
    assertEquals(0, SqliteNativeBootstrap.activeConnectionCount(bookPath));
    assertTrue(Files.isDirectory(markerPath));
  }

  @Test
  void rollbackOpeningConnection_preservesBookEntryWhenAnotherConnectionRemains() {
    Path bookPath = tempDirectory.resolve("rollback-helper.sqlite").toAbsolutePath().normalize();
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath = markerConnectionsByBookPath();
    int baselineConnections = activeConnections.get();
    AtomicInteger activeBookConnections = new AtomicInteger(2);

    activeConnections.set(baselineConnections + 2);
    activeConnectionsByBookPath.put(bookPath, activeBookConnections);
    try {
      invokeRollbackOpeningConnection(bookPath, activeBookConnections, null);

      assertEquals(baselineConnections + 1, activeConnections.get());
      assertEquals(1, activeBookConnections.get());
      assertEquals(activeBookConnections, activeConnectionsByBookPath.get(bookPath));
      assertFalse(markerConnectionsByBookPath.containsKey(bookPath));
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
      markerConnectionsByBookPath.remove(bookPath);
    }
  }

  @Test
  void rollbackOpeningConnection_preservesOneMarkerRegistryEntryWhenMarkerCountRemainsPositive() {
    Path bookPath =
        tempDirectory
            .resolve("rollback-helper-marker-positive.sqlite")
            .toAbsolutePath()
            .normalize();
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath = markerConnectionsByBookPath();
    int baselineConnections = activeConnections.get();
    AtomicInteger activeBookConnections = new AtomicInteger(2);
    AtomicInteger markerConnections = new AtomicInteger(2);

    activeConnections.set(baselineConnections + 2);
    activeConnectionsByBookPath.put(bookPath, activeBookConnections);
    markerConnectionsByBookPath.put(bookPath, markerConnections);
    try {
      invokeRollbackOpeningConnection(bookPath, activeBookConnections, markerConnections);

      assertEquals(baselineConnections + 1, activeConnections.get());
      assertEquals(1, activeBookConnections.get());
      assertEquals(1, markerConnections.get());
      assertEquals(markerConnections, markerConnectionsByBookPath.get(bookPath));
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
      markerConnectionsByBookPath.remove(bookPath);
    }
  }

  @Test
  void rollbackOpeningConnection_removesMarkerAndBookEntriesWhenTheLastCountsRollBack() {
    Path bookPath =
        tempDirectory.resolve("rollback-helper-marker.sqlite").toAbsolutePath().normalize();
    AtomicInteger activeConnections = activeConnections();
    ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath = activeConnectionsByBookPath();
    ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath = markerConnectionsByBookPath();
    int baselineConnections = activeConnections.get();
    AtomicInteger activeBookConnections = new AtomicInteger(1);
    AtomicInteger markerConnections = new AtomicInteger(1);

    activeConnections.set(baselineConnections + 1);
    activeConnectionsByBookPath.put(bookPath, activeBookConnections);
    markerConnectionsByBookPath.put(bookPath, markerConnections);
    try {
      invokeRollbackOpeningConnection(bookPath, activeBookConnections, markerConnections);

      assertEquals(baselineConnections, activeConnections.get());
      assertFalse(activeConnectionsByBookPath.containsKey(bookPath));
      assertFalse(markerConnectionsByBookPath.containsKey(bookPath));
    } finally {
      activeConnections.set(baselineConnections);
      activeConnectionsByBookPath.remove(bookPath);
      markerConnectionsByBookPath.remove(bookPath);
    }
  }

  @Test
  void close_threeArgumentOverload_delegatesToDefaultMarkerPublishingClosePath() throws Exception {
    Path bookPath = tempDirectory.resolve("close-overload.sqlite");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    Path activityMarkerPath = activityMarkerPath(normalizedBookPath);
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    int baselineConnections = SqliteNativeBootstrap.activeConnectionCount();

    try (ManagedOpenedDatabase database =
        ManagedOpenedDatabase.open(bookPath, sqliteApi, "close overload")) {
      assertEquals(baselineConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
      assertTrue(Files.exists(activityMarkerPath));

      assertDoesNotThrow(database::closeWithThreeArgumentOverload);

      assertEquals(baselineConnections, SqliteNativeBootstrap.activeConnectionCount());
      assertFalse(Files.exists(activityMarkerPath));
    }
  }

  private static Path activityMarkerPath(Path normalizedBookPath) {
    return normalizedBookPath.resolveSibling(
        normalizedBookPath.getFileName()
            + ".fingrind-activity-"
            + SqliteProcessIdentity.current().activityMarkerFileToken()
            + ".marker");
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath() {
    return (ConcurrentMap<Path, AtomicInteger>) ACTIVE_CONNECTIONS_BY_BOOK_PATH.get();
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath() {
    return (ConcurrentMap<Path, AtomicInteger>) MARKER_CONNECTIONS_BY_BOOK_PATH.get();
  }

  private static AtomicInteger activeConnections() {
    return (AtomicInteger) ACTIVE_CONNECTIONS.get();
  }

  private static VarHandle staticVarHandle(String fieldName, Class<?> fieldType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteNativeBootstrap.class, MethodHandles.lookup());
      return lookup.findStaticVarHandle(SqliteNativeBootstrap.class, fieldName, fieldType);
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      throw new LinkageError(
          "Failed to bind SQLite native bootstrap field: " + fieldName, exception);
    }
  }

  private static MethodHandle bootstrapHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteNativeBootstrap.class, MethodHandles.lookup());
      return lookup.findStatic(SqliteNativeBootstrap.class, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite native bootstrap helper: " + methodName, exception);
    }
  }

  private static void invokeRollbackOpeningConnection(
      Path normalizedBookPath,
      AtomicInteger activeBookConnections,
      @Nullable AtomicInteger markerConnections) {
    try {
      ROLLBACK_OPENING_CONNECTION.invokeExact(
          normalizedBookPath, activeBookConnections, markerConnections);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite native bootstrap rollback helper.", throwable);
    }
  }

  private static SqliteNativeApi closeBehaviorApi(String helperMethodName, AtomicInteger closeCalls)
      throws ReflectiveOperationException {
    SqliteNativeApi baseApi = SqliteNativeBootstrap.api();
    SqliteNativeCalls.AddressToIntCall delegateClose =
        SqliteNativeCalls.addressToInt(baseApi.sqlite3CloseV2());
    return SqliteNativeApiTestSupport.withCloseV2(
        baseApi,
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findStatic(
                    SqliteNativeBridgeTestSupport.class,
                    helperMethodName,
                    java.lang.invoke.MethodType.methodType(
                        int.class,
                        AtomicInteger.class,
                        SqliteNativeCalls.AddressToIntCall.class,
                        MemorySegment.class)),
            0,
            closeCalls,
            delegateClose));
  }

  /**
   * AutoCloseable wrapper that exercises the three-argument close overload without double-close.
   */
  private static final class ManagedOpenedDatabase implements AutoCloseable {
    private final SqliteNativeDatabase database;
    private final Path normalizedBookPath;
    private final SqliteNativeApi sqliteApi;
    private boolean closed;

    private ManagedOpenedDatabase(
        SqliteNativeDatabase database, Path normalizedBookPath, SqliteNativeApi sqliteApi) {
      this.database = Objects.requireNonNull(database, "database");
      this.normalizedBookPath = Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
      this.sqliteApi = Objects.requireNonNull(sqliteApi, "sqliteApi");
    }

    private static ManagedOpenedDatabase open(
        Path bookPath, SqliteNativeApi sqliteApi, String passphraseDescription) {
      try (SqliteBookPassphrase passphrase =
          SqliteBookPassphrase.fromCharacters(passphraseDescription, TEST_BOOK_KEY.toCharArray())) {
        return new ManagedOpenedDatabase(
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi),
            bookPath.toAbsolutePath().normalize(),
            sqliteApi);
      }
    }

    private void closeWithThreeArgumentOverload() {
      if (closed) {
        return;
      }
      SqliteNativeConnections.close(database.handle(), normalizedBookPath, sqliteApi);
      closed = true;
    }

    @Override
    public void close() {
      if (!closed) {
        database.close();
        closed = true;
      }
    }
  }
}
