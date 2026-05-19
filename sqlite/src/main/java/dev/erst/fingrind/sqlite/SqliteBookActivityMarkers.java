package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Same-directory process-liveness markers for active protected-book access across processes. */
final class SqliteBookActivityMarkers {
  private static final String ACTIVITY_PREFIX_SEGMENT = ".fingrind-activity-";
  private static final String ACTIVITY_FILE_SUFFIX = ".marker";

  private SqliteBookActivityMarkers() {}

  static void createCurrentProcessMarker(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Path markerPath = currentProcessMarkerPath(normalizedBookPath);
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedBookPath);
      Files.deleteIfExists(markerPath);
      Files.writeString(
          markerPath,
          SqliteProcessIdentity.current().leaseMetadataText(),
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      SqliteBookFileSecurity.hardenOwnerOnlyFile(markerPath);
    } catch (FileAlreadyExistsException ignored) {
      try {
        SqliteBookFileSecurity.hardenOwnerOnlyFile(markerPath);
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to publish one FinGrind SQLite book activity marker.", exception);
      }
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
      if (!Files.isRegularFile(sibling, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      String siblingFileName =
          Objects.requireNonNull(sibling.getFileName(), "sibling fileName").toString();
      if (!siblingFileName.startsWith(expectedPrefix)
          || !siblingFileName.endsWith(ACTIVITY_FILE_SUFFIX)) {
        continue;
      }
      String identityToken =
          siblingFileName.substring(
              expectedPrefix.length(), siblingFileName.length() - ACTIVITY_FILE_SUFFIX.length());
      SqliteProcessIdentity markerIdentity =
          SqliteProcessIdentity.fromActivityMarkerFileName(identityToken);
      if (markerIdentity == null) {
        Files.deleteIfExists(sibling);
        continue;
      }
      if (markerIdentity.isCurrentProcess()) {
        continue;
      }
      if (markerIdentity.isLive()) {
        return true;
      }
      Files.deleteIfExists(sibling);
    }
    return false;
  }

  private static Path currentProcessMarkerPath(Path normalizedBookPath) {
    return normalizedBookPath.resolveSibling(
        activityFilePrefix(normalizedBookPath)
            + SqliteProcessIdentity.current().activityMarkerFileToken()
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
