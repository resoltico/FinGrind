package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns process-local connection-activity accounting for the SQLite native runtime. */
final class SqliteNativeRuntimeActivity {
  private SqliteNativeRuntimeActivity() {}

  static void recordOpeningConnection(Path normalizedBookPath) {
    recordOpeningConnection(normalizedBookPath, true);
  }

  static void recordOpeningConnection(Path normalizedBookPath, boolean publishesActivityMarker) {
    SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"), publishesActivityMarker);
  }

  static void recordConnectionClosed(@Nullable Path normalizedBookPath) {
    recordConnectionClosed(normalizedBookPath, true);
  }

  static void recordConnectionClosed(
      @Nullable Path normalizedBookPath, boolean publishesActivityMarker) {
    SqliteNativeConnectionActivityRegistry.recordConnectionClosed(
        normalizedBookPath, publishesActivityMarker);
  }

  static int activeConnectionCount() {
    return SqliteNativeConnectionActivityRegistry.activeConnectionCount();
  }

  static int activeConnectionCount(Path normalizedBookPath) {
    return SqliteNativeConnectionActivityRegistry.activeConnectionCount(
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
  }

  static boolean hasExternalActiveConnections(Path normalizedBookPath) {
    return SqliteNativeConnectionActivityRegistry.hasExternalActiveConnections(
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
  }
}
