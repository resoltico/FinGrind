package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Admits public repository corpus directories without treating committed source as private output.
 *
 * <p>Regression seeds and their metadata are reviewable repository inputs, so they intentionally
 * do not require owner-only artifact permissions. This boundary still refuses symlinked or
 * non-directory corpus ancestors and creates missing directory components one at a time before a
 * fresh candidate file is published.
 */
final class RegressionSeedRepositoryPathAdmission {
  private RegressionSeedRepositoryPathAdmission() {}

  /** Resolves one existing project root to its real directory identity. */
  static Path canonicalProjectDirectory(Path projectDirectory) throws IOException {
    Path requestedProjectDirectory =
        Objects.requireNonNull(projectDirectory, "projectDirectory").toAbsolutePath().normalize();
    Path canonicalProjectDirectory = requestedProjectDirectory.toRealPath();
    if (!Files.isDirectory(canonicalProjectDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Jazzer regression seed project root must resolve to an existing real directory: "
              + requestedProjectDirectory);
    }
    return canonicalProjectDirectory;
  }

  /**
   * Returns whether one project-relative directory is wholly present as real no-follow directories.
   *
   * <p>A missing component is ordinary for an unpopulated corpus. A symbolic link or another
   * non-directory component is never interpreted as an empty corpus directory.
   */
  static boolean hasExistingRealDirectoryTree(Path projectDirectory, Path directory)
      throws IOException {
    Path canonicalProjectDirectory = canonicalProjectDirectory(projectDirectory);
    Path normalizedDirectory = requireProjectRelativeDirectory(canonicalProjectDirectory, directory);
    Path currentDirectory = canonicalProjectDirectory;
    for (Path component : canonicalProjectDirectory.relativize(normalizedDirectory)) {
      currentDirectory = currentDirectory.resolve(component);
      if (Files.notExists(currentDirectory, LinkOption.NOFOLLOW_LINKS)) {
        return false;
      }
      requireRealDirectory(currentDirectory);
    }
    return true;
  }

  /**
   * Creates missing repository-relative components sequentially and rejects all symlinked
   * ancestors.
   */
  static Path createOrRequireRealDirectoryTree(Path projectDirectory, Path directory)
      throws IOException {
    Path canonicalProjectDirectory = canonicalProjectDirectory(projectDirectory);
    Path normalizedDirectory = requireProjectRelativeDirectory(canonicalProjectDirectory, directory);
    Path currentDirectory = canonicalProjectDirectory;
    for (Path component : canonicalProjectDirectory.relativize(normalizedDirectory)) {
      currentDirectory = currentDirectory.resolve(component);
      createOrRequireRealDirectory(currentDirectory);
    }
    return currentDirectory;
  }

  private static Path requireProjectRelativeDirectory(Path projectDirectory, Path directory)
      throws IOException {
    Path normalizedDirectory =
        Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    if (!normalizedDirectory.startsWith(projectDirectory)) {
      throw new IOException(
          "Jazzer regression seed directory must remain beneath its project root: "
              + normalizedDirectory);
    }
    return normalizedDirectory;
  }

  private static void createOrRequireRealDirectory(Path directory) throws IOException {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Files.createDirectory(directory);
      } catch (FileAlreadyExistsException racedCreation) {
        // A concurrent creator is acceptable only when it produced the exact real directory.
      }
    }
    requireRealDirectory(directory);
  }

  private static void requireRealDirectory(Path directory) throws IOException {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Jazzer regression seed directory must be one real non-symlink directory: " + directory);
    }
  }
}
