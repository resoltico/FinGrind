package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Native connection activity registration bound to one stable physical book identity.
 *
 * <p>The original path is retained only for diagnostics. Closure never re-resolves it, so a rename,
 * replacement, or deletion after open cannot leak the activity slot or decrement a different
 * object's process-local count.
 */
final class SqliteNativeActivityRegistration {
  private final Path diagnosticBookPath;
  private final String objectIdentity;
  private final SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration;

  SqliteNativeActivityRegistration(
      Path diagnosticBookPath,
      String objectIdentity,
      SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration) {
    this.diagnosticBookPath = Objects.requireNonNull(diagnosticBookPath, "diagnosticBookPath");
    this.objectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
    this.activityRegistration = activityRegistration;
  }

  Path diagnosticBookPath() {
    return diagnosticBookPath;
  }

  String objectIdentity() {
    return objectIdentity;
  }

  SqliteBookActivityMarkers.@Nullable ActivityRegistration activityRegistration() {
    return activityRegistration;
  }

  boolean publishesActivityMarker() {
    return activityRegistration != null;
  }

  void releaseActivityMarker() {
    if (activityRegistration != null) {
      activityRegistration.close();
    }
  }
}
