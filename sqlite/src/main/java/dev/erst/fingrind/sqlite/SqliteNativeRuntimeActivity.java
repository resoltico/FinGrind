package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns physical-object connection-activity accounting for the SQLite native runtime. */
final class SqliteNativeRuntimeActivity {
  private SqliteNativeRuntimeActivity() {}

  /** Registers one native connection and returns its exact stable close token. */
  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath, boolean publishesActivityMarker) {
    return SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"), publishesActivityMarker);
  }

  /** Closes exactly one registration without re-resolving its original path. */
  static void recordConnectionClosed(@Nullable SqliteNativeActivityRegistration registration) {
    SqliteNativeConnectionActivityRegistry.recordConnectionClosed(registration);
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
