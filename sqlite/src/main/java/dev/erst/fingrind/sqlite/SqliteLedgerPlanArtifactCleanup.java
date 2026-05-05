package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Removes transient protected-book artifacts created for a rolled-back ledger plan. */
final class SqliteLedgerPlanArtifactCleanup {
  private SqliteLedgerPlanArtifactCleanup() {}

  static void cleanupCreatedMissingBookArtifacts(
      Path bookPath,
      @Nullable Path existingAncestorBoundary,
      @Nullable SqliteNativeDatabase databaseToClose) {
    closeCreatedCleanupDatabase(databaseToClose);
    deleteCreatedMissingBookArtifacts(bookPath, existingAncestorBoundary);
  }

  static void deleteCreatedMissingBookArtifacts(
      Path bookPath, @Nullable Path existingAncestorBoundary) {
    String baseFileName =
        Objects.requireNonNull(bookPath.getFileName(), "bookPath fileName").toString();
    deleteBookArtifactIfPresent(bookPath.resolveSibling(baseFileName + "-journal"));
    deleteBookArtifactIfPresent(bookPath.resolveSibling(baseFileName + "-wal"));
    deleteBookArtifactIfPresent(bookPath.resolveSibling(baseFileName + "-shm"));
    deleteBookArtifactIfPresent(bookPath);
    deleteEmptyCreatedParentDirectories(
        Objects.requireNonNull(bookPath.getParent(), "bookPath parent"), existingAncestorBoundary);
  }

  static void deleteBookArtifactIfPresent(Path artifactPath) {
    try {
      Files.deleteIfExists(artifactPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to remove SQLite book artifact created during rolled-back plan cleanup: "
              + artifactPath,
          exception);
    }
  }

  static void deleteEmptyCreatedParentDirectories(
      Path startingDirectory, @Nullable Path existingAncestorBoundary) {
    Path currentDirectory = startingDirectory;
    while (currentDirectory != null && !currentDirectory.equals(existingAncestorBoundary)) {
      try {
        if (!Files.deleteIfExists(currentDirectory)) {
          currentDirectory = currentDirectory.getParent();
          continue;
        }
      } catch (DirectoryNotEmptyException exception) {
        return;
      } catch (NoSuchFileException exception) {
        currentDirectory = currentDirectory.getParent();
        continue;
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to remove an empty SQLite book directory created during rolled-back plan cleanup: "
                + currentDirectory,
            exception);
      }
      currentDirectory = currentDirectory.getParent();
    }
  }

  static @Nullable Path nearestExistingAncestor(Path path) {
    Path ancestor = path.toAbsolutePath().normalize().getParent();
    while (ancestor != null && Files.notExists(ancestor)) {
      ancestor = ancestor.getParent();
    }
    return ancestor;
  }

  static void closeCreatedCleanupDatabase(@Nullable SqliteNativeDatabase databaseToClose) {
    if (databaseToClose == null) {
      return;
    }
    try {
      databaseToClose.close();
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Failed to close the SQLite book created during rolled-back plan cleanup.", exception);
    }
  }
}
