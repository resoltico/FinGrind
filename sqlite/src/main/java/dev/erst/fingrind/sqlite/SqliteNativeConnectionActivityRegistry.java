package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** Tracks process-local native connection activity by stable physical book identity. */
final class SqliteNativeConnectionActivityRegistry {
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();
  private static final ConcurrentMap<String, AtomicInteger> ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY =
      new ConcurrentHashMap<>();
  private static final Set<SqliteNativeActivityRegistration> ACTIVE_REGISTRATIONS =
      ConcurrentHashMap.newKeySet();

  private SqliteNativeConnectionActivityRegistry() {}

  /** Constructs the close token after the process and physical-object counts are retained. */
  @FunctionalInterface
  interface ActivityRegistrationFactory {
    /** Returns the exact token that may release one successfully opened native connection. */
    SqliteNativeActivityRegistration create(
        Path diagnosticBookPath,
        String objectIdentity,
        SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration);
  }

  /** Registers one opening connection and returns the exact token required to close it. */
  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath, boolean publishesActivityMarker) {
    return recordOpeningConnection(
        normalizedBookPath,
        publishesActivityMarker,
        SqliteNativeActivityRegistration::new);
  }

  /** Records one opening connection through an explicit close-token construction boundary. */
  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath,
      boolean publishesActivityMarker,
      ActivityRegistrationFactory registrationFactory) {
    Path checkedBookPath =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath")
            .toAbsolutePath()
            .normalize();
    SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration = null;
    @Nullable String objectIdentity = null;
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
      ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.compute(
          resolvedObjectIdentity,
          (ignored, existingConnections) -> {
            AtomicInteger connections =
                existingConnections == null ? new AtomicInteger() : existingConnections;
            connections.incrementAndGet();
            return connections;
          });
      objectCountIncremented = true;
      SqliteNativeActivityRegistration registration =
          Objects.requireNonNull(registrationFactory, "registrationFactory")
              .create(checkedBookPath, resolvedObjectIdentity, activityRegistration);
      ACTIVE_REGISTRATIONS.add(registration);
      return registration;
    } catch (IOException exception) {
      IllegalStateException failure =
          new IllegalStateException(
              "Failed to establish the physical identity for one SQLite native connection.",
              exception);
      rollbackOpeningConnection(
          objectIdentity,
          processCountIncremented,
          objectCountIncremented,
          activityRegistration,
          failure);
      throw failure;
    } catch (RuntimeException | Error failure) {
      rollbackOpeningConnection(
          objectIdentity,
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
    if (!ACTIVE_REGISTRATIONS.remove(registration)) {
      throw new IllegalStateException(
          "One SQLite native connection activity registration was not issued by the active "
              + "SQLite connection registry for "
              + registration.diagnosticBookPath()
              + ".");
    }
    decrementActiveConnections();
    decrementActiveObjectConnections(registration);
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
      boolean processCountIncremented,
      boolean objectCountIncremented,
      SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration,
      Throwable primaryFailure) {
    if (objectCountIncremented) {
      decrementActiveObjectConnectionsForRollback(
          Objects.requireNonNull(objectIdentity, "objectIdentity"));
    }
    if (processCountIncremented) {
      decrementActiveConnectionsForRollback();
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
    ACTIVE_CONNECTIONS.decrementAndGet();
  }

  private static void decrementActiveConnectionsForRollback() {
    ACTIVE_CONNECTIONS.decrementAndGet();
  }

  private static void decrementActiveObjectConnections(
      SqliteNativeActivityRegistration registration) {
    String objectIdentity = registration.objectIdentity();
    ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.compute(
        objectIdentity,
        (ignored, activeObjectConnections) -> {
          AtomicInteger checkedObjectConnections =
              Objects.requireNonNull(activeObjectConnections, "activeObjectConnections");
          return checkedObjectConnections.decrementAndGet() == 0 ? null : checkedObjectConnections;
        });
  }

  private static void decrementActiveObjectConnectionsForRollback(String objectIdentity) {
    ACTIVE_CONNECTIONS_BY_OBJECT_IDENTITY.compute(
        objectIdentity,
        (ignored, activeObjectConnections) -> {
          AtomicInteger checkedObjectConnections =
              Objects.requireNonNull(activeObjectConnections, "activeObjectConnections");
          return checkedObjectConnections.decrementAndGet() == 0 ? null : checkedObjectConnections;
        });
  }
}
