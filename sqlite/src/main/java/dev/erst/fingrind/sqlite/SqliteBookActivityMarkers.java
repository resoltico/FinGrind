package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Same-directory process-liveness markers for active protected-book access across processes. */
final class SqliteBookActivityMarkers {
  private static final String ACTIVITY_PREFIX_SEGMENT = ".fingrind-activity-";
  private static final String ACTIVITY_FILE_SUFFIX = ".marker";
  private static final Duration UNKNOWN_START_EXTERNAL_MARKER_GRACE_PERIOD = Duration.ofHours(12);

  private SqliteBookActivityMarkers() {}

  static void createCurrentProcessMarker(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Path markerPath = currentProcessMarkerPath(normalizedBookPath);
    try {
      ensureMarkerDirectory(normalizedBookPath, markerPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to publish one FinGrind SQLite book activity marker.", exception);
    }
  }

  static void deleteCurrentProcessMarker(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Path markerPath = currentProcessMarkerPath(normalizedBookPath);
    try {
      Files.deleteIfExists(markerPath);
    } catch (IOException exception) {
      SqliteBestEffort.reportCleanupFailure(
          "deleting one SQLite book activity marker at " + markerPath, exception);
    }
  }

  static boolean hasExternalLiveMarker(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    try {
      return scanForExternalLiveMarkers(normalizedBookPath);
    } catch (IOException exception) {
      throw activityMarkerScanFailure(exception);
    }
  }

  private static IllegalStateException activityMarkerScanFailure(IOException cause) {
    return new IllegalStateException(
        "Failed to inspect or clear one FinGrind SQLite book activity marker.", cause);
  }

  private static boolean scanForExternalLiveMarkers(Path normalizedBookPath) throws IOException {
    Path parentDirectory = requireParentDirectory(normalizedBookPath);
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    String expectedPrefix = activityFilePrefix(normalizedBookPath);
    return scanSiblingDirectory(parentDirectory, expectedPrefix);
  }

  private static boolean scanSiblingDirectory(Path parentDirectory, String expectedPrefix)
      throws IOException {
    try (DirectoryStream<Path> siblings = Files.newDirectoryStream(parentDirectory)) {
      return scanSiblingEntries(siblings, expectedPrefix);
    }
  }

  private static boolean scanSiblingEntries(DirectoryStream<Path> siblings, String expectedPrefix)
      throws IOException {
    for (Path sibling : siblings) {
      String siblingFileName =
          Objects.requireNonNull(sibling.getFileName(), "sibling fileName").toString();
      if (!siblingFileName.startsWith(expectedPrefix)
          || !siblingFileName.endsWith(ACTIVITY_FILE_SUFFIX)) {
        continue;
      }
      if (!Files.isDirectory(sibling, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      String identityToken =
          siblingFileName.substring(
              expectedPrefix.length(), siblingFileName.length() - ACTIVITY_FILE_SUFFIX.length());
      SqliteProcessIdentity markerIdentity =
          SqliteProcessIdentity.fromCoordinationToken(identityToken);
      if (markerIdentity == null) {
        if (!deleteMarkerAndVerifyMissing(sibling)) {
          return true;
        }
        continue;
      }
      if (markerIdentity.isCurrentProcess()) {
        continue;
      }
      Instant markerLastModified =
          Files.getLastModifiedTime(sibling, LinkOption.NOFOLLOW_LINKS).toInstant();
      if (markerIdentity.isLiveWhenUnlocked(
          markerLastModified, UNKNOWN_START_EXTERNAL_MARKER_GRACE_PERIOD)) {
        return true;
      }
      if (!deleteMarkerAndVerifyMissing(sibling)) {
        return true;
      }
    }
    return false;
  }

  private static boolean deleteMarkerAndVerifyMissing(Path markerPath) throws IOException {
    Files.deleteIfExists(markerPath);
    return !Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS);
  }

  private static void ensureMarkerDirectory(Path normalizedBookPath, Path markerPath)
      throws IOException {
    SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedBookPath);
    if (Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
      requireExistingDirectory(markerPath);
      SqliteBookFileSecurity.hardenDirectory(markerPath);
      return;
    }
    Files.createDirectory(markerPath);
    SqliteBookFileSecurity.hardenDirectory(markerPath);
  }

  private static void requireExistingDirectory(Path markerPath) throws IOException {
    if (!Files.isDirectory(markerPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Activity marker path already exists as a non-directory entry.");
    }
  }

  private static Path currentProcessMarkerPath(Path normalizedBookPath) {
    return normalizedBookPath.resolveSibling(
        activityFilePrefix(normalizedBookPath)
            + SqliteProcessIdentity.current().coordinationToken()
            + ACTIVITY_FILE_SUFFIX);
  }

  private static String activityFilePrefix(Path normalizedBookPath) {
    String fileName =
        Objects.requireNonNull(normalizedBookPath.getFileName(), "normalizedBookPath fileName")
            .toString();
    return fileName + ACTIVITY_PREFIX_SEGMENT;
  }

  private static Path requireParentDirectory(Path normalizedBookPath) {
    Path parentDirectory = normalizedBookPath.getParent();
    if (parentDirectory == null) {
      throw new IllegalArgumentException(
          "The FinGrind SQLite book path must resolve beneath one parent directory: "
              + normalizedBookPath);
    }
    return parentDirectory;
  }
}
