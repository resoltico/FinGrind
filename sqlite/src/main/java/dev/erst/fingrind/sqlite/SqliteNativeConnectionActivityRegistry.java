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
        normalizedBookPath, publishesActivityMarker, SqliteNativeActivityRegistration::new);
  }

  /** Records one opening connection through an explicit close-token construction boundary. */
  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath,
      boolean publishesActivityMarker,
      ActivityRegistrationFactory registrationFactory) {
    return recordOpeningConnection(
        normalizedBookPath,
        publishesActivityMarker,
        registrationFactory,
        SqliteObjectCoordinationArtifacts::physicalIdentity);
  }

  static SqliteNativeActivityRegistration recordOpeningConnection(
      Path normalizedBookPath,
      boolean publishesActivityMarker,
      ActivityRegistrationFactory registrationFactory,
      PhysicalIdentityResolver physicalIdentityResolver) {
    Path checkedBookPath =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath")
            .toAbsolutePath()
            .normalize();
    @Nullable String objectIdentity = null;
    boolean processCountIncremented = false;
    boolean objectCountIncremented = false;
    @Nullable SqliteNativeActivityRegistration activeRegistration = null;
    boolean openingCompleted = false;
    SqliteNativeActivityRegistration issuedRegistration;
    try {
      try (SqlitePendingActivityRegistration pendingRegistration =
          SqlitePendingActivityRegistration.acquire(checkedBookPath, publishesActivityMarker)) {
        if (publishesActivityMarker) {
          objectIdentity = pendingRegistration.requiredObjectIdentity();
        } else {
          objectIdentity =
              Objects.requireNonNull(physicalIdentityResolver, "physicalIdentityResolver")
                  .resolve(checkedBookPath);
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
        issuedRegistration =
            Objects.requireNonNull(
                Objects.requireNonNull(registrationFactory, "registrationFactory")
                    .create(
                        checkedBookPath,
                        resolvedObjectIdentity,
                        pendingRegistration.registration()),
                "registration");
        if (!ACTIVE_REGISTRATIONS.add(issuedRegistration)) {
          throw new IllegalStateException(
              "The SQLite native-connection registry rejected a newly issued registration.");
        }
        activeRegistration = issuedRegistration;
        pendingRegistration.commit(issuedRegistration);
      }
      openingCompleted = true;
      return issuedRegistration;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to establish the physical identity for one SQLite native connection.", exception);
    } finally {
      if (!openingCompleted) {
        try {
          releaseUncommittedRegistration(activeRegistration);
        } finally {
          rollbackOpeningConnection(
              objectIdentity, processCountIncremented, objectCountIncremented);
        }
      }
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
    return activeConnectionCount(
        normalizedBookPath, SqliteObjectCoordinationArtifacts::physicalIdentity);
  }

  static int activeConnectionCount(
      Path normalizedBookPath, PhysicalIdentityResolver physicalIdentityResolver) {
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
              Objects.requireNonNull(physicalIdentityResolver, "physicalIdentityResolver")
                  .resolve(checkedBookPath));
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

  /** Resolves the stable physical identity for one admitted SQLite book path. */
  @FunctionalInterface
  interface PhysicalIdentityResolver {
    /** Resolves the stable physical identity for the supplied path. */
    String resolve(Path path) throws IOException;
  }

  private static void rollbackOpeningConnection(
      @Nullable String objectIdentity,
      boolean processCountIncremented,
      boolean objectCountIncremented) {
    if (objectCountIncremented) {
      decrementActiveObjectConnectionsForRollback(
          Objects.requireNonNull(objectIdentity, "objectIdentity"));
    }
    if (processCountIncremented) {
      decrementActiveConnectionsForRollback();
    }
  }

  private static void releaseUncommittedRegistration(
      @Nullable SqliteNativeActivityRegistration activeRegistration) {
    if (activeRegistration == null) {
      return;
    }
    ACTIVE_REGISTRATIONS.remove(activeRegistration);
    activeRegistration.releaseActivityMarker();
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
