package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Shared closed-copy filesystem helpers for backup, restore, and rollback recovery. */
final class SqliteBookMaintenanceFiles {
  private static final List<String> SQLITE_SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");

  private SqliteBookMaintenanceFiles() {}

  static Path normalize(Path path, String argumentName) {
    Objects.requireNonNull(argumentName, "argumentName");
    Objects.requireNonNull(path, argumentName);
    return path.toAbsolutePath().normalize();
  }

  static List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
    return blockingArtifacts(normalizedBookPath);
  }

  static List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
    return blockingArtifacts(normalizedBackupFilePath);
  }

  private static List<Path> blockingArtifacts(Path normalizedBasePath) {
    try {
      List<Path> blockingArtifacts = new ArrayList<>(sqliteSidecars(normalizedBasePath));
      blockingArtifacts.addAll(SqliteRekeyRollbackFile.staleRollbackArtifacts(normalizedBasePath));
      blockingArtifacts.sort(Comparator.comparing(Path::toString));
      return List.copyOf(blockingArtifacts);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect SQLite maintenance sidecars beside " + normalizedBasePath + ".",
          exception);
    }
  }

  private static List<Path> sqliteSidecars(Path normalizedBasePath) {
    String baseName =
        Objects.requireNonNull(normalizedBasePath.getFileName(), "normalizedBasePath fileName")
            .toString();
    return SQLITE_SIDECAR_SUFFIXES.stream()
        .map(suffix -> normalizedBasePath.resolveSibling(baseName + suffix))
        .filter(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS))
        .sorted(Comparator.comparing(Path::toString))
        .toList();
  }

  static void copyFreshBook(Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
      Files.copy(
          normalizedSourceBookPath, normalizedTargetBookPath, StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(normalizedTargetBookPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to copy the FinGrind SQLite book from "
              + normalizedSourceBookPath
              + " to "
              + normalizedTargetBookPath
              + ".",
          exception);
    }
  }

  static void replaceBook(Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    Path stagedCopy = null;
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
      Path targetParentDirectory =
          Objects.requireNonNull(
              normalizedTargetBookPath.getParent(), "normalizedTargetBookPath parent");
      String targetFileName =
          Objects.requireNonNull(
                  normalizedTargetBookPath.getFileName(), "normalizedTargetBookPath fileName")
              .toString();
      stagedCopy =
          Files.createTempFile(targetParentDirectory, targetFileName + ".restore-", ".tmp");
      Files.copy(
          normalizedSourceBookPath,
          stagedCopy,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(stagedCopy);
      moveReplacing(stagedCopy, normalizedTargetBookPath);
      SqliteBookFileSecurity.hardenBookArtifacts(normalizedTargetBookPath);
      stagedCopy = null;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to restore the FinGrind SQLite book from "
              + normalizedSourceBookPath
              + " to "
              + normalizedTargetBookPath
              + ".",
          exception);
    } finally {
      if (stagedCopy != null) {
        SqliteFileCleanup.deleteQuietly(stagedCopy);
      }
    }
  }

  static void deleteRollbackArtifact(Path normalizedRollbackArtifactPath) {
    try {
      Files.deleteIfExists(normalizedRollbackArtifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to delete the FinGrind SQLite rollback artifact "
              + normalizedRollbackArtifactPath
              + ".",
          exception);
    }
  }

  private static void moveReplacing(Path stagedCopy, Path normalizedTargetBookPath)
      throws IOException {
    try {
      Files.move(
          stagedCopy,
          normalizedTargetBookPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(stagedCopy, normalizedTargetBookPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
