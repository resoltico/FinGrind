package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** Tracks process-local and per-book SQLite native connection activity. */
final class SqliteNativeConnectionActivityRegistry {
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();
  private static final ConcurrentMap<Path, AtomicInteger> ACTIVE_CONNECTIONS_BY_BOOK_PATH =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<Path, AtomicInteger> MARKER_CONNECTIONS_BY_BOOK_PATH =
      new ConcurrentHashMap<>();

  private SqliteNativeConnectionActivityRegistry() {}

  static void recordOpeningConnection(Path normalizedBookPath, boolean publishesActivityMarker) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    ACTIVE_CONNECTIONS.incrementAndGet();
    AtomicInteger activeBookConnections =
        ACTIVE_CONNECTIONS_BY_BOOK_PATH.computeIfAbsent(
            normalizedBookPath, ignored -> new AtomicInteger());
    activeBookConnections.incrementAndGet();
    if (!publishesActivityMarker) {
      return;
    }
    AtomicInteger markerConnections =
        MARKER_CONNECTIONS_BY_BOOK_PATH.computeIfAbsent(
            normalizedBookPath, ignored -> new AtomicInteger());
    int openedMarkerConnections = markerConnections.incrementAndGet();
    if (openedMarkerConnections == 1) {
      try {
        SqliteBookActivityMarkers.createCurrentProcessMarker(normalizedBookPath);
      } catch (RuntimeException exception) {
        rollbackOpeningConnection(normalizedBookPath, activeBookConnections, markerConnections);
        throw exception;
      }
    }
  }

  static void recordConnectionClosed(
      @Nullable Path normalizedBookPath, boolean publishesActivityMarker) {
    decrementActiveConnections();
    if (normalizedBookPath == null) {
      return;
    }
    AtomicInteger activeBookConnections = requireActiveBookConnections(normalizedBookPath);
    int remainingBookConnections =
        decrementActiveBookConnections(normalizedBookPath, activeBookConnections);
    if (!publishesActivityMarker) {
      removeActiveBookConnectionsWhenClosed(
          normalizedBookPath, activeBookConnections, remainingBookConnections);
      return;
    }
    decrementMarkerConnections(normalizedBookPath, activeBookConnections);
    removeActiveBookConnectionsWhenClosed(
        normalizedBookPath, activeBookConnections, remainingBookConnections);
  }

  static int activeConnectionCount() {
    return ACTIVE_CONNECTIONS.get();
  }

  static int activeConnectionCount(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    AtomicInteger activeBookConnections = ACTIVE_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    return activeBookConnections == null ? 0 : activeBookConnections.get();
  }

  static boolean hasExternalActiveConnections(Path normalizedBookPath) {
    return SqliteBookActivityMarkers.hasExternalLiveMarker(normalizedBookPath);
  }

  private static void rollbackOpeningConnection(
      Path normalizedBookPath,
      AtomicInteger activeBookConnections,
      @Nullable AtomicInteger markerConnections) {
    if (markerConnections != null) {
      int remainingMarkerConnections = markerConnections.decrementAndGet();
      if (remainingMarkerConnections == 0) {
        MARKER_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, markerConnections);
      }
    }
    int remainingBookConnections = activeBookConnections.decrementAndGet();
    if (remainingBookConnections == 0) {
      ACTIVE_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, activeBookConnections);
    }
    ACTIVE_CONNECTIONS.decrementAndGet();
  }

  private static void decrementActiveConnections() {
    int remainingConnections = ACTIVE_CONNECTIONS.decrementAndGet();
    if (remainingConnections < 0) {
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException("SQLite active connection count underflow.");
    }
  }

  private static AtomicInteger requireActiveBookConnections(Path normalizedBookPath) {
    AtomicInteger activeBookConnections = ACTIVE_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    if (activeBookConnections != null) {
      return activeBookConnections;
    }
    ACTIVE_CONNECTIONS.incrementAndGet();
    throw new IllegalStateException(
        "SQLite active connection registry missing the normalized book path entry for "
            + normalizedBookPath
            + ".");
  }

  private static int decrementActiveBookConnections(
      Path normalizedBookPath, AtomicInteger activeBookConnections) {
    int remainingBookConnections = activeBookConnections.decrementAndGet();
    if (remainingBookConnections >= 0) {
      return remainingBookConnections;
    }
    activeBookConnections.incrementAndGet();
    ACTIVE_CONNECTIONS.incrementAndGet();
    throw new IllegalStateException(
        "SQLite active connection count underflow for normalized book path "
            + normalizedBookPath
            + ".");
  }

  private static void removeActiveBookConnectionsWhenClosed(
      Path normalizedBookPath, AtomicInteger activeBookConnections, int remainingBookConnections) {
    if (remainingBookConnections == 0) {
      ACTIVE_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, activeBookConnections);
    }
  }

  private static void decrementMarkerConnections(
      Path normalizedBookPath, AtomicInteger activeBookConnections) {
    AtomicInteger markerConnections = MARKER_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    if (markerConnections == null) {
      activeBookConnections.incrementAndGet();
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite activity-marker registry missing the normalized book path entry for "
              + normalizedBookPath
              + ".");
    }
    int remainingMarkerConnections = markerConnections.decrementAndGet();
    if (remainingMarkerConnections < 0) {
      markerConnections.incrementAndGet();
      activeBookConnections.incrementAndGet();
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite activity-marker connection count underflow for normalized book path "
              + normalizedBookPath
              + ".");
    }
    if (remainingMarkerConnections == 0) {
      MARKER_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, markerConnections);
      SqliteBookActivityMarkers.deleteCurrentProcessMarker(normalizedBookPath);
    }
  }
}
