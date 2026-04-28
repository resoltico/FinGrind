package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Same-directory rollback artifact for one in-place SQLite rekey attempt. */
final class SqliteRekeyRollbackFile {
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
          Files.createTempFile(parentDirectory, fileName + ".rekey-rollback-", ".sqlite");
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

  private static Path requireBookParentDirectory(Path normalizedBookPath) {
    Path parentDirectory = normalizedBookPath.getParent();
    if (parentDirectory == null) {
      throw new IllegalArgumentException(
          "The FinGrind SQLite book path must resolve to a file beneath a parent directory: "
              + normalizedBookPath);
    }
    return parentDirectory;
  }
}
