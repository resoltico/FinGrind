package dev.erst.fingrind.sqlite;

import static java.lang.System.Logger.Level.WARNING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Same-directory rollback artifact for one in-place SQLite rekey attempt. */
final class SqliteRekeyRollbackFile {
  private static final System.Logger LOGGER =
      System.getLogger(SqliteRekeyRollbackFile.class.getName());
  private static final String ROLLBACK_NAME_SEGMENT = ".rekey-rollback-";
  private static final String ROLLBACK_FILE_SUFFIX = ".sqlite";

  private final Path path;

  SqliteRekeyRollbackFile(Path path) {
    this.path = path;
  }

  static SqliteRekeyRollbackFile create(Path normalizedBookPath) {
    try {
      Path parentDirectory = requireBookParentDirectory(normalizedBookPath);
      String fileName =
          Objects.requireNonNull(normalizedBookPath.getFileName(), "normalizedBookPath fileName")
              .toString();
      Path rollbackPath =
          Files.createTempFile(parentDirectory, rollbackFilePrefix(fileName), ROLLBACK_FILE_SUFFIX);
      Files.copy(
          normalizedBookPath,
          rollbackPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(rollbackPath);
      return new SqliteRekeyRollbackFile(rollbackPath);
    } catch (IOException exception) {
      throw new SqliteStorageFailureException(
          "Failed to create the FinGrind SQLite rekey rollback copy.", exception);
    }
  }

  void restore(Path normalizedBookPath) {
    try {
      Files.copy(
          path,
          normalizedBookPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(normalizedBookPath);
    } catch (IOException exception) {
      throw new SqliteStorageFailureException(
          "Failed to restore the FinGrind SQLite book from the rekey rollback copy at "
              + path
              + ".",
          exception);
    }
  }

  void deleteQuietly() {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      SqliteBestEffort.reportCleanupFailure(
          "deleting the SQLite rekey rollback copy at " + path, exception);
    }
  }

  Path path() {
    return path;
  }

  static void reportStaleRollbackArtifacts(Path normalizedBookPath) {
    reportStaleRollbackArtifacts(
        normalizedBookPath,
        SqliteRekeyRollbackFile::logStaleRollbackArtifacts,
        SqliteRekeyRollbackFile::logRollbackArtifactScanFailure);
  }

  static void reportStaleRollbackArtifacts(
      Path normalizedBookPath,
      StaleRollbackReporter reporter,
      RollbackArtifactScanFailureReporter failureReporter) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    Objects.requireNonNull(reporter, "reporter");
    Objects.requireNonNull(failureReporter, "failureReporter");
    try {
      List<Path> rollbackArtifacts = findStaleRollbackArtifacts(normalizedBookPath);
      if (!rollbackArtifacts.isEmpty()) {
        reporter.report(normalizedBookPath, rollbackArtifacts);
      }
    } catch (IOException exception) {
      failureReporter.report(normalizedBookPath, exception);
    }
  }

  static List<Path> findStaleRollbackArtifacts(Path normalizedBookPath) throws IOException {
    Path parentDirectory = requireBookParentDirectory(normalizedBookPath);
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return List.of();
    }
    String fileName =
        Objects.requireNonNull(normalizedBookPath.getFileName(), "normalizedBookPath fileName")
            .toString();
    try (Stream<Path> siblings = Files.list(parentDirectory)) {
      return siblings
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .filter(path -> isRollbackArtifact(path, fileName))
          .map(path -> path.toAbsolutePath().normalize())
          .sorted(Comparator.comparing(Path::toString))
          .collect(Collectors.toUnmodifiableList());
    }
  }

  private static Path requireBookParentDirectory(Path normalizedBookPath) {
    Path parentDirectory = normalizedBookPath.getParent();
    if (parentDirectory == null) {
      throw new IllegalArgumentException(
          "The FinGrind SQLite book path must resolve to a file beneath a parent directory: "
              + normalizedBookPath);
    }
    return parentDirectory;
  }

  private static boolean isRollbackArtifact(Path candidatePath, String baseFileName) {
    String candidateName =
        Objects.requireNonNull(candidatePath.getFileName(), "candidatePath fileName").toString();
    return candidateName.startsWith(rollbackFilePrefix(baseFileName))
        && candidateName.endsWith(ROLLBACK_FILE_SUFFIX);
  }

  private static String rollbackFilePrefix(String baseFileName) {
    return baseFileName + ROLLBACK_NAME_SEGMENT;
  }

  private static void logStaleRollbackArtifacts(
      Path normalizedBookPath, List<Path> rollbackArtifacts) {
    LOGGER.log(
        WARNING,
        "Found stale FinGrind SQLite rekey rollback artifacts beside "
            + normalizedBookPath
            + ": "
            + rollbackArtifacts.stream().map(Path::toString).collect(Collectors.joining(", "))
            + ". Inspect these encrypted copies explicitly before deleting them.");
  }

  private static void logRollbackArtifactScanFailure(
      Path normalizedBookPath, IOException exception) {
    LOGGER.log(
        WARNING,
        "Failed to scan for stale FinGrind SQLite rekey rollback artifacts beside "
            + normalizedBookPath
            + ".",
        exception);
  }

  /** Receives one discovered stale rollback-artifact set for one protected book path. */
  @FunctionalInterface
  interface StaleRollbackReporter {
    /** Reports the discovered rollback artifacts for the supplied normalized book path. */
    void report(Path normalizedBookPath, List<Path> rollbackArtifacts);
  }

  /** Receives one directory-scan failure that prevented stale rollback-artifact discovery. */
  @FunctionalInterface
  interface RollbackArtifactScanFailureReporter {
    /** Reports the directory-scan failure for the supplied normalized book path. */
    void report(Path normalizedBookPath, IOException exception);
  }
}
