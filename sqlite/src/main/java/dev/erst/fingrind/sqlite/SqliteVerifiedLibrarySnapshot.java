package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Supplier;

/** Private verified copy of one selected managed SQLite library plus its checksum sidecars. */
record SqliteVerifiedLibrarySnapshot(
    SqliteLibraryTarget sourceTarget,
    Path snapshotDirectory,
    Path snapshotLibraryPath,
    Path snapshotChecksumPath) {
  SqliteVerifiedLibrarySnapshot {
    Objects.requireNonNull(sourceTarget, "sourceTarget");
    snapshotDirectory = SqliteManagedLibraryDigestSupport.normalizedLibraryPath(snapshotDirectory);
    snapshotLibraryPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(snapshotLibraryPath);
    snapshotChecksumPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(snapshotChecksumPath);
    if (!snapshotLibraryPath.startsWith(snapshotDirectory)) {
      throw new IllegalArgumentException("snapshotLibraryPath must live inside snapshotDirectory.");
    }
    if (!snapshotChecksumPath.startsWith(snapshotDirectory)) {
      throw new IllegalArgumentException(
          "snapshotChecksumPath must live inside snapshotDirectory.");
    }
  }

  static SqliteVerifiedLibrarySnapshot copyOf(
      SqliteLibraryTarget sourceTarget, Path sourceLibraryPath, Path sourceChecksumPath) {
    return copyOf(
        sourceTarget,
        sourceLibraryPath,
        sourceChecksumPath,
        SqliteManagedLibrarySnapshotSecurity::createPrivateSnapshotDirectory);
  }

  static SqliteVerifiedLibrarySnapshot copyOf(
      SqliteLibraryTarget sourceTarget,
      Path sourceLibraryPath,
      Path sourceChecksumPath,
      Supplier<Path> snapshotDirectoryFactory) {
    Path snapshotDirectory =
        Objects.requireNonNull(snapshotDirectoryFactory, "snapshotDirectoryFactory").get();
    Path snapshotLibraryPath = snapshotDirectory.resolve(sourceLibraryPath.getFileName());
    Path snapshotChecksumPath = snapshotDirectory.resolve(sourceChecksumPath.getFileName());
    SqliteManagedLibrarySnapshotSecurity.registerDeleteOnExit(snapshotDirectory);
    SqliteManagedLibrarySnapshotSecurity.registerDeleteOnExit(snapshotLibraryPath);
    SqliteManagedLibrarySnapshotSecurity.registerDeleteOnExit(snapshotChecksumPath);
    try {
      Files.copy(sourceLibraryPath, snapshotLibraryPath, StandardCopyOption.REPLACE_EXISTING);
      Files.copy(sourceChecksumPath, snapshotChecksumPath, StandardCopyOption.REPLACE_EXISTING);
      SqliteManagedLibrarySnapshotSecurity.hardenPrivateFile(snapshotLibraryPath);
      SqliteManagedLibrarySnapshotSecurity.hardenPrivateFile(snapshotChecksumPath);
    } catch (IOException exception) {
      deleteQuietly(snapshotChecksumPath);
      deleteQuietly(snapshotLibraryPath);
      deleteQuietly(snapshotDirectory);
      throw new IllegalStateException(
          "Failed to create the private managed SQLite verification snapshot from "
              + sourceLibraryPath
              + ".",
          exception);
    }
    return new SqliteVerifiedLibrarySnapshot(
        sourceTarget, snapshotDirectory, snapshotLibraryPath, snapshotChecksumPath);
  }

  SqliteLibraryTarget runtimeTarget() {
    return new SqliteLibraryTarget(
        sourceTarget.mode(), sourceTarget.provenance(), snapshotLibraryPath.toString());
  }

  void deleteQuietly() {
    deleteQuietly(snapshotChecksumPath);
    deleteQuietly(snapshotLibraryPath);
    deleteQuietly(snapshotDirectory);
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup only; the snapshot is already scoped to the current user temp root.
    }
  }
}
