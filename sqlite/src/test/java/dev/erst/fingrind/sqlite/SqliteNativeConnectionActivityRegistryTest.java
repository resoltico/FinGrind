package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Focused regression coverage for native connection-activity accounting edge cases. */
class SqliteNativeConnectionActivityRegistryTest {
  @BeforeEach
  @AfterEach
  void resetRegistryState() {
    activeConnections().set(0);
    activeConnectionsByBookPath().clear();
    markerConnectionsByBookPath().clear();
  }

  @Test
  void recordOpeningConnection_rollsBackRegistryStateWhenMarkerPublicationFails() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      AclFixturePath markerPath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-activity-"
                  + SqliteProcessIdentity.current().activityMarkerFileToken()
                  + ".marker");
      markerPath.failCreateDirectoryWith(new IOException("marker-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath));
      assertEquals(
          "Failed to publish one FinGrind SQLite book activity marker.", exception.getMessage());
      assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
      assertTrue(markerConnectionsByBookPath().isEmpty());
    }
  }

  @Test
  void recordConnectionClosed_rejectsProcessConnectionUnderflow() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordConnectionClosed(null, false));
    assertEquals("SQLite active connection count underflow.", exception.getMessage());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void recordConnectionClosed_rejectsPerBookConnectionUnderflow() {
    Path normalizedBookPath = Path.of("/tmp/per-book-underflow.sqlite");
    activeConnections().set(1);
    activeConnectionsByBookPath().put(normalizedBookPath, new AtomicInteger());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordConnectionClosed(normalizedBookPath, false));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("SQLite active connection count underflow"));
    assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, activeConnectionsByBookPath().get(normalizedBookPath).get());
  }

  @Test
  void recordConnectionClosed_rejectsMissingMarkerRegistryEntry() {
    Path normalizedBookPath = Path.of("/tmp/missing-marker-entry.sqlite");
    activeConnections().set(1);
    activeConnectionsByBookPath().put(normalizedBookPath, new AtomicInteger(1));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordConnectionClosed(normalizedBookPath, true));
    assertTrue(NullTestSupport.messageOf(exception).contains("activity-marker registry missing"));
    assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(1, activeConnectionsByBookPath().get(normalizedBookPath).get());
  }

  @Test
  void recordConnectionClosed_rejectsMarkerConnectionUnderflow() {
    Path normalizedBookPath = Path.of("/tmp/marker-underflow.sqlite");
    activeConnections().set(1);
    activeConnectionsByBookPath().put(normalizedBookPath, new AtomicInteger(1));
    markerConnectionsByBookPath().put(normalizedBookPath, new AtomicInteger());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordConnectionClosed(normalizedBookPath, true));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("activity-marker connection count underflow"));
    assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(1, activeConnectionsByBookPath().get(normalizedBookPath).get());
    assertEquals(0, markerConnectionsByBookPath().get(normalizedBookPath).get());
  }

  @Test
  void rollbackOpeningConnection_allowsMissingMarkerCounterWhenConnectionsRemain() {
    Path normalizedBookPath = Path.of("/tmp/rollback-without-marker.sqlite");
    AtomicInteger activeBookConnections = new AtomicInteger(2);
    activeConnections().set(1);

    invokeRollbackOpeningConnection(normalizedBookPath, activeBookConnections, null);

    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(1, activeBookConnections.get());
  }

  @Test
  void rollbackOpeningConnection_preservesRegistryEntriesWhenCountersRemainPositive() {
    Path normalizedBookPath = Path.of("/tmp/rollback-with-marker.sqlite");
    AtomicInteger activeBookConnections = new AtomicInteger(2);
    AtomicInteger markerConnections = new AtomicInteger(2);
    activeConnections().set(1);

    invokeRollbackOpeningConnection(normalizedBookPath, activeBookConnections, markerConnections);

    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(1, activeBookConnections.get());
    assertEquals(1, markerConnections.get());
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<Path, AtomicInteger> activeConnectionsByBookPath() {
    return (ConcurrentMap<Path, AtomicInteger>) staticField("ACTIVE_CONNECTIONS_BY_BOOK_PATH");
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<Path, AtomicInteger> markerConnectionsByBookPath() {
    return (ConcurrentMap<Path, AtomicInteger>) staticField("MARKER_CONNECTIONS_BY_BOOK_PATH");
  }

  private static AtomicInteger activeConnections() {
    return (AtomicInteger) staticField("ACTIVE_CONNECTIONS");
  }

  private static Object staticField(String fieldName) {
    try {
      Field field = SqliteNativeConnectionActivityRegistry.class.getDeclaredField(fieldName);
      VarHandle handle =
          MethodHandles.privateLookupIn(
                  SqliteNativeConnectionActivityRegistry.class, MethodHandles.lookup())
              .findStaticVarHandle(
                  SqliteNativeConnectionActivityRegistry.class, fieldName, field.getType());
      return handle.get();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Failed to access SQLite native connection activity test fixture state `"
              + fieldName
              + "`.",
          exception);
    }
  }

  private static void invokeRollbackOpeningConnection(
      Path normalizedBookPath,
      AtomicInteger activeBookConnections,
      @Nullable AtomicInteger markerConnections) {
    try {
      MethodHandle methodHandle =
          MethodHandles.privateLookupIn(
                  SqliteNativeConnectionActivityRegistry.class, MethodHandles.lookup())
              .findStatic(
                  SqliteNativeConnectionActivityRegistry.class,
                  "rollbackOpeningConnection",
                  MethodType.methodType(
                      void.class, Path.class, AtomicInteger.class, AtomicInteger.class));
      methodHandle.invokeExact(normalizedBookPath, activeBookConnections, markerConnections);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Failed to invoke SQLite native connection rollback helper for tests.", exception);
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to invoke SQLite native connection rollback helper for tests.", throwable);
    }
  }
}
