package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Retained v4 directory-reservation coordination for one canonical maintenance-directory domain.
 *
 * <p>The fixed owner-only file is durable protocol state, not a stale artifact to reclaim. A held
 * exclusive lock after the immutable header is the sole liveness fact; an unlocked valid file is
 * inert after a process crash. Earlier v2/v3 and legacy lease namespaces are hard protocol breaks
 * and block without parsing, deleting, or adopting their contents.
 */
final class SqliteMaintenanceLeaseArtifacts {
  private static final String CONTROL_FILE_NAME = ".fingrind-maintenance-directory-v4.control";
  private static final String CONTROL_PROTOCOL = "FinGrind-maintenance-directory-v4";
  private static final String RETIRED_V3_CONTROL_FILE_NAME =
      ".fingrind-maintenance-directory-v3.control";
  private static final String RETIRED_V2_CONTROL_FILE_NAME =
      ".fingrind-maintenance-directory-v2.control";

  private SqliteMaintenanceLeaseArtifacts() {}

  /** Acquires the exclusive v4 directory reservation for one existing secure canonical domain. */
  static @Nullable SqliteLeaseHandle acquire(Path canonicalDirectory) throws IOException {
    Path checkedDirectory = normalizedDirectory(canonicalDirectory);
    if (hasLegacyLeaseResidue(checkedDirectory)) {
      return null;
    }
    Path controlPath = controlFilePath(checkedDirectory);
    return retainedLease(
        controlPath,
        SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
            controlPath,
            magic(checkedDirectory),
            SqliteCoordinationControlProtocol.maintenanceLockPosition(),
            SqliteCoordinationControlProtocol.maintenanceLockLength()));
  }

  /**
   * Returns whether v4 lock state or retired lease residue prevents a new operation.
   *
   * <p>A malformed control file, a lock error, and an overlapping in-process lock are all
   * fail-closed.
   */
  static boolean hasBlockingArtifact(Path canonicalDirectory) throws IOException {
    Path checkedDirectory = normalizedDirectory(canonicalDirectory);
    if (!Files.isDirectory(checkedDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    if (hasLegacyLeaseResidue(checkedDirectory)) {
      return true;
    }
    Path controlPath = controlFilePath(checkedDirectory);
    if (Files.notExists(controlPath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try {
      try (SqliteCoordinationControlFiles.@Nullable LockedControlFile probe =
          SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
              controlPath,
              magic(checkedDirectory),
              SqliteCoordinationControlProtocol.maintenanceLockPosition(),
              SqliteCoordinationControlProtocol.maintenanceLockLength())) {
        return probe == null;
      }
    } catch (IOException | RuntimeException invalidOrUnavailable) {
      return true;
    }
  }

  static Path controlFilePath(Path canonicalDirectory) {
    return normalizedDirectory(canonicalDirectory).resolve(CONTROL_FILE_NAME);
  }

  private static byte[] magic(Path canonicalDirectory) {
    return SqliteCoordinationControlProtocol.magic(
        CONTROL_PROTOCOL,
        SqliteCoordinationControlProtocol.canonicalDirectoryBinding(canonicalDirectory));
  }

  private static boolean hasLegacyLeaseResidue(Path canonicalDirectory) throws IOException {
    return Files.isDirectory(canonicalDirectory, LinkOption.NOFOLLOW_LINKS)
        && SqliteDirectoryStreams.read(
            canonicalDirectory,
            entries -> {
              for (Path entry : entries) {
                String name =
                    Objects.requireNonNull(entry.getFileName(), "entry fileName").toString();
                if (isRetiredLeaseName(name)) {
                  return true;
                }
              }
              return false;
            });
  }

  /** Detects retired lease namespaces without parsing, deleting, or adopting their contents. */
  private static boolean isRetiredLeaseName(String fileName) {
    return RETIRED_V3_CONTROL_FILE_NAME.equals(fileName)
        || RETIRED_V2_CONTROL_FILE_NAME.equals(fileName)
        || ".fingrind-maintenance.lock".equals(fileName)
        || fileName.endsWith(".fingrind-maintenance.lock")
        || (fileName.contains(".fingrind-maintenance-") && fileName.endsWith(".lock"));
  }

  private static Path normalizedDirectory(Path directory) {
    return Objects.requireNonNull(directory, "canonicalDirectory").toAbsolutePath().normalize();
  }

  private static @Nullable SqliteLeaseHandle retainedLease(
      Path controlPath, SqliteCoordinationControlFiles.@Nullable LockedControlFile lockedControl) {
    return lockedControl == null ? null : new SqliteLeaseHandle(controlPath, lockedControl);
  }
}
