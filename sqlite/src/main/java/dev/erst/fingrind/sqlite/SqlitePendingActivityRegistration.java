package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Owns a newly acquired activity marker until a native-connection token has been recorded.
 *
 * <p>The holder makes opening failure atomic with respect to the marker: leaving its scope closes
 * the marker unless {@link #commit(SqliteNativeActivityRegistration)} has made the issued native
 * registration its sole owner.
 */
final class SqlitePendingActivityRegistration implements AutoCloseable {
  private final AtomicReference<SqliteBookActivityMarkers.@Nullable ActivityRegistration>
      registration;

  private SqlitePendingActivityRegistration(
      SqliteBookActivityMarkers.@Nullable ActivityRegistration registration) {
    this.registration = new AtomicReference<>(registration);
  }

  /** Acquires the optional marker required by one native connection opening. */
  static SqlitePendingActivityRegistration acquire(
      Path normalizedBookPath, boolean publishesActivityMarker) {
    return new SqlitePendingActivityRegistration(
        publishesActivityMarker
            ? SqliteBookActivityMarkers.acquireCurrentProcessActivity(
                Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"))
            : null);
  }

  /** Returns the held marker for token construction without transferring its closure authority. */
  SqliteBookActivityMarkers.@Nullable ActivityRegistration registration() {
    return registration.get();
  }

  /** Returns the marker's stable object identity when this connection publishes a marker. */
  String requiredObjectIdentity() {
    return Objects.requireNonNull(registration.get(), "activity marker registration")
        .objectIdentity();
  }

  /**
   * Transfers marker closure authority only when the issued token retained the exact borrowed
   * marker instance.
   */
  void commit(SqliteNativeActivityRegistration issuedRegistration) {
    SqliteNativeActivityRegistration checkedRegistration =
        Objects.requireNonNull(issuedRegistration, "issuedRegistration");
    if (!registration.compareAndSet(checkedRegistration.activityRegistration(), null)) {
      throw new IllegalStateException(
          "The issued SQLite native-connection registration did not retain its borrowed activity marker.");
    }
  }

  /** Releases the marker when native-connection registration did not complete. */
  @Override
  public void close() {
    try (SqliteBookActivityMarkers.@Nullable ActivityRegistration _ =
        registration.getAndSet(null)) {
      // The uncommitted activity marker closes on scope exit.
    }
  }
}
