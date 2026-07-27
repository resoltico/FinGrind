package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Shared lexical no-follow and canonical-path admission for protected-book maintenance artifacts.
 */
final class SqliteBookMaintenanceFiles {
  private static final List<String> SQLITE_SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");

  private SqliteBookMaintenanceFiles() {}

  /**
   * Resolves a caller-selected existing maintenance source after proving its complete lexical
   * ancestry and final leaf are real, regular filesystem entries.
   */
  static Path normalizeExistingSource(Path path, String argumentName) {
    return normalize(path, argumentName, LeafRequirement.EXISTING_REGULAR_FILE);
  }

  /**
   * Resolves a caller-selected maintenance publication target after proving its lexical ancestry.
   *
   * <p>The final leaf may remain absent because the operation-specific publication policy owns
   * whether an existing regular target is admissible.
   */
  static Path normalizeOptionalArtifact(Path path, String argumentName) {
    return normalize(path, argumentName, LeafRequirement.ABSENT_OR_REGULAR_FILE);
  }

  private static Path normalize(Path path, String argumentName, LeafRequirement leafRequirement) {
    Objects.requireNonNull(argumentName, "argumentName");
    LeafRequirement checkedLeafRequirement =
        Objects.requireNonNull(leafRequirement, "leafRequirement");
    Path requestedPath = Objects.requireNonNull(path, argumentName).toAbsolutePath();
    Path parentDirectory = requiredParentDirectory(requestedPath);
    Path fileName = requiredFileName(requestedPath);
    requireExistingParentDirectory(requestedPath, parentDirectory);
    // The no-follow walk intentionally precedes normalization and real-path resolution. A lexical
    // component that later collapses through `..` must not hide an alias into another directory.
    SqlitePrivateOutputDirectoryAdmission.requireOwnerOnlyNonMutableAncestry(
        requestedPath, parentDirectory);
    try {
      return resolveCanonicalTarget(
          requestedPath, parentDirectory.toRealPath(), fileName, checkedLeafRequirement);
    } catch (IOException | SecurityException exception) {
      throw new SqliteCallerPathContractException(
          requestedPath,
          SqliteCallerPathFailure.PARENT_PATH_COLLISION,
          "The FinGrind protected-book maintenance path cannot establish one real parent directory: "
              + SqliteMachinePaths.absoluteValue(requestedPath),
          exception);
    }
  }

  private static Path resolveCanonicalTarget(
      Path requestedPath, Path canonicalParent, Path fileName, LeafRequirement leafRequirement)
      throws IOException {
    Path canonicalTarget = canonicalParent.resolve(fileName);
    requireNonSymlinkTarget(requestedPath, canonicalTarget);
    if (Files.exists(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
      return requireCanonicalExistingTarget(requestedPath, canonicalParent, canonicalTarget);
    }
    requireExistingLeafWhenRequired(requestedPath, canonicalTarget, leafRequirement);
    return canonicalTarget;
  }

  private static void requireNonSymlinkTarget(Path requestedPath, Path canonicalTarget) {
    if (Files.isSymbolicLink(canonicalTarget)) {
      throw new SqliteCallerPathContractException(
          requestedPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected-book maintenance artifact cannot be a symbolic link: "
              + SqliteMachinePaths.absoluteValue(canonicalTarget));
    }
  }

  private static Path requireCanonicalExistingTarget(
      Path requestedPath, Path canonicalParent, Path canonicalTarget) throws IOException {
    if (!Files.isRegularFile(canonicalTarget, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          requestedPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected-book maintenance artifact must be one regular file: "
              + SqliteMachinePaths.absoluteValue(canonicalTarget));
    }
    Path canonicalExistingTarget = canonicalTarget.toRealPath(LinkOption.NOFOLLOW_LINKS);
    if (!canonicalParent.equals(canonicalExistingTarget.getParent())) {
      throw new SqliteCallerPathContractException(
          requestedPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected-book maintenance target escaped its admitted parent: "
              + SqliteMachinePaths.absoluteValue(canonicalExistingTarget));
    }
    return canonicalExistingTarget;
  }

  private static void requireExistingLeafWhenRequired(
      Path requestedPath, Path canonicalTarget, LeafRequirement leafRequirement) {
    if (leafRequirement == LeafRequirement.EXISTING_REGULAR_FILE) {
      throw new SqliteCallerPathContractException(
          requestedPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind protected-book maintenance source must be an existing regular file: "
              + SqliteMachinePaths.absoluteValue(canonicalTarget));
    }
  }

  private static Path requiredParentDirectory(Path requestedPath) {
    Path parentDirectory = requestedPath.getParent();
    if (parentDirectory == null) {
      throw missingParentDirectory(requestedPath);
    }
    return parentDirectory;
  }

  private static Path requiredFileName(Path requestedPath) {
    Path fileName = requestedPath.getFileName();
    if (fileName == null) {
      throw missingParentDirectory(requestedPath);
    }
    return fileName;
  }

  private static void requireExistingParentDirectory(Path requestedPath, Path parentDirectory) {
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw parentDirectoryFailure(requestedPath, parentDirectory);
    }
  }

  private static SqliteCallerPathContractException missingParentDirectory(Path requestedPath) {
    return new SqliteCallerPathContractException(
        requestedPath,
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        "The FinGrind protected-book maintenance path requires an existing parent directory: "
            + SqliteMachinePaths.absoluteValue(requestedPath));
  }

  /** The admissible final-leaf states after lexical ancestry admission. */
  private enum LeafRequirement {
    EXISTING_REGULAR_FILE,
    ABSENT_OR_REGULAR_FILE
  }

  private static SqliteCallerPathContractException parentDirectoryFailure(
      Path requestedPath, Path parentDirectory) {
    SqliteCallerPathFailure failure =
        Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)
            ? SqliteCallerPathFailure.PARENT_PATH_COLLISION
            : SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY;
    return new SqliteCallerPathContractException(
        requestedPath,
        failure,
        "The FinGrind protected-book maintenance path requires an existing real parent directory: "
            + SqliteMachinePaths.absoluteValue(parentDirectory));
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
