package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** Tracks process-local native connection activity by stable physical book identity. */
final class SqliteNativeConnectionActivityRegistry {
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();
  private static final ConcurrentMap<String, AtomicInteger> ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY =
      new ConcurrentHashMap<>();

  private SqliteNativeConnectionActivityRegistry() {}

  /** Registers one opening connection and returns the exact token required to close it. */
  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath, boolean publishesActivityMarker) {
    Path checkedBookPath =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath")
            .toAbsolutePath()
            .normalize();
    SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration = null;
    @Nullable String objectIdentity = null;
    @Nullable AtomicInteger activeObjectConnections = null;
    boolean processCountIncremented = false;
    boolean objectCountIncremented = false;
    try {
      if (publishesActivityMarker) {
        activityRegistration =
            SqliteBookActivityMarkers.acquireCurrentProcessActivity(checkedBookPath);
        objectIdentity = activityRegistration.objectIdentity();
      } else {
        objectIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(checkedBookPath);
      }
      String resolvedObjectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
      ACTIVE_CONNECTIONS.incrementAndGet();
      processCountIncremented = true;
      activeObjectConnections =
          ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.computeIfAbsent(
              resolvedObjectIdentity, ignored -> new AtomicInteger());
      activeObjectConnections.incrementAndGet();
      objectCountIncremented = true;
      return new SqliteNativeActivityRegistration(
          checkedBookPath, resolvedObjectIdentity, activityRegistration);
    } catch (IOException exception) {
      IllegalStateException failure =
          new IllegalStateException(
              "Failed to establish the physical identity for one SQLite native connection.",
              exception);
      rollbackOpeningConnection(
          objectIdentity,
          activeObjectConnections,
          processCountIncremented,
          objectCountIncremented,
          activityRegistration,
          failure);
      throw failure;
    } catch (RuntimeException | Error failure) {
      rollbackOpeningConnection(
          objectIdentity,
          activeObjectConnections,
          processCountIncremented,
          objectCountIncremented,
          activityRegistration,
          failure);
      throw failure;
    }
  }

  /** Releases exactly the registration returned for one successfully opened native connection. */
  static void recordConnectionClosed(@Nullable SqliteNativeActivityRegistration registration) {
    if (registration == null) {
      return;
    }
    if (!registration.claimClose()) {
      throw new IllegalStateException(
          "One SQLite native connection activity registration was already closed for "
              + registration.diagnosticBookPath()
              + ".");
    }
    String objectIdentity = registration.objectIdentity();
    AtomicInteger activeObjectConnections = requireActiveObjectConnections(registration);
    decrementActiveConnections();
    int remainingObjectConnections =
        decrementActiveObjectConnections(registration, activeObjectConnections);
    if (remainingObjectConnections == 0) {
      ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.remove(objectIdentity, activeObjectConnections);
    }
    registration.releaseActivityMarker();
  }

  static int activeConnectionCount() {
    return ACTIVE_CONNECTIONS.get();
  }

  static int activeConnectionCount(Path normalizedBookPath) {
    Path checkedBookPath =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath")
            .toAbsolutePath()
            .normalize();
    if (!Files.isRegularFile(checkedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return 0;
    }
    try {
      AtomicInteger activeObjectConnections =
          ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.get(
              SqliteObjectCoordinationArtifacts.physicalIdentity(checkedBookPath));
      return activeObjectConnections == null ? 0 : activeObjectConnections.get();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to establish the physical identity for one SQLite activity query.", exception);
    }
  }

  static boolean hasExternalActiveConnections(Path normalizedBookPath) {
    return SqliteBookActivityMarkers.hasExternalLiveMarker(
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
  }

  private static void rollbackOpeningConnection(
      @Nullable String objectIdentity,
      @Nullable AtomicInteger activeObjectConnections,
      boolean processCountIncremented,
      boolean objectCountIncremented,
      SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration,
      Throwable primaryFailure) {
    if (objectCountIncremented) {
      AtomicInteger checkedObjectConnections =
          Objects.requireNonNull(activeObjectConnections, "activeObjectConnections");
      int remainingObjectConnections = checkedObjectConnections.decrementAndGet();
      if (remainingObjectConnections == 0) {
        ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.remove(
            Objects.requireNonNull(objectIdentity, "objectIdentity"), checkedObjectConnections);
      }
    }
    if (processCountIncremented) {
      decrementActiveConnectionsForRollback(primaryFailure);
    }
    if (activityRegistration != null) {
      try {
        activityRegistration.close();
      } catch (RuntimeException | Error closeFailure) {
        primaryFailure.addSuppressed(closeFailure);
      }
    }
  }

  private static void decrementActiveConnections() {
    int remainingConnections = ACTIVE_CONNECTIONS.decrementAndGet();
    if (remainingConnections < 0) {
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException("SQLite active connection count underflow.");
    }
  }

  private static void decrementActiveConnectionsForRollback(Throwable primaryFailure) {
    int remainingConnections = ACTIVE_CONNECTIONS.decrementAndGet();
    if (remainingConnections < 0) {
      ACTIVE_CONNECTIONS.incrementAndGet();
      primaryFailure.addSuppressed(
          new IllegalStateException("SQLite active connection count underflow during rollback."));
    }
  }

  private static AtomicInteger requireActiveObjectConnections(
      SqliteNativeActivityRegistration registration) {
    AtomicInteger activeObjectConnections =
        ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.get(registration.objectIdentity());
    if (activeObjectConnections != null) {
      return activeObjectConnections;
    }
    throw new IllegalStateException(
        "SQLite active connection registry missing the physical book identity entry for "
            + registration.diagnosticBookPath()
            + ".");
  }

  private static int decrementActiveObjectConnections(
      SqliteNativeActivityRegistration registration, AtomicInteger activeObjectConnections) {
    int remainingObjectConnections = activeObjectConnections.decrementAndGet();
    if (remainingObjectConnections >= 0) {
      return remainingObjectConnections;
    }
    activeObjectConnections.incrementAndGet();
    ACTIVE_CONNECTIONS.incrementAndGet();
    throw new IllegalStateException(
        "SQLite active connection count underflow for physical book identity "
            + registration.objectIdentity()
            + " ("
            + registration.diagnosticBookPath()
            + ").");
  }
}
