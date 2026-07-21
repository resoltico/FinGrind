package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Shared filesystem helpers for protected-book maintenance artifact paths. */
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
    List<Path> blockingArtifacts = new ArrayList<>(sqliteSidecars(normalizedBasePath));
    blockingArtifacts.sort(Comparator.comparing(Path::toString));
    return List.copyOf(blockingArtifacts);
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
}
