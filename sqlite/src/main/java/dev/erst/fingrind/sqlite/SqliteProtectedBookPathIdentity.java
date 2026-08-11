package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Separates caller-submitted path spelling from physical filesystem identity for pair publication.
 *
 * <p>Provider {@link Path#equals(Object)} may case-fold even where a directory is configured as
 * case-sensitive. It is therefore never an authority predicate for an immutable pair binding.
 * Binding and evidence membership use normalized submitted spelling; physical-parent decisions use
 * {@link Files#isSameFile(Path, Path)} and fail closed when the provider cannot establish them.
 */
final class SqliteProtectedBookPathIdentity {
  private SqliteProtectedBookPathIdentity() {}

  static boolean sameNormalizedSpelling(Path first, Path second) {
    return normalizedSpelling(first).equals(normalizedSpelling(second));
  }

  static List<Path> distinctPhysicalParents(Path firstTargetPath, Path secondTargetPath) {
    Path checkedFirstTarget = Objects.requireNonNull(firstTargetPath, "firstTargetPath");
    Path checkedSecondTarget = Objects.requireNonNull(secondTargetPath, "secondTargetPath");
    Path firstParent = parentOf(checkedFirstTarget);
    Path secondParent = parentOf(checkedSecondTarget);
    boolean samePhysicalParent =
        sameExistingFilesystemObject(firstParent, secondParent, checkedFirstTarget);
    if (sameNormalizedSpelling(firstParent, secondParent) && !samePhysicalParent) {
      throw identityUnestablished(
          checkedFirstTarget,
          new IOException(
              "One normalized protected-book parent spelling did not resolve to one filesystem object."));
    }
    if (samePhysicalParent) {
      return List.of(firstParent);
    }
    return List.of(firstParent, secondParent).stream()
        .sorted(Comparator.comparing(SqliteProtectedBookPathIdentity::normalizedSpelling))
        .toList();
  }

  static boolean sameExistingFilesystemObject(Path first, Path second, Path requestedPath) {
    Path checkedFirst = Objects.requireNonNull(first, "first");
    Path checkedSecond = Objects.requireNonNull(second, "second");
    try {
      return Files.isSameFile(checkedFirst, checkedSecond);
    } catch (IOException | RuntimeException failure) {
      throw identityUnestablished(Objects.requireNonNull(requestedPath, "requestedPath"), failure);
    }
  }

  static String normalizedSpelling(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
  }

  private static Path parentOf(Path path) {
    return Objects.requireNonNull(
        Objects.requireNonNull(path, "path").getParent(), "protected-book pair path parent");
  }

  private static SqliteCallerPathContractException identityUnestablished(
      Path requestedPath, Throwable cause) {
    return new SqliteCallerPathContractException(
        requestedPath,
        SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED,
        "FinGrind could not establish one required protected-book filesystem identity: "
            + SqliteMachinePaths.absoluteValue(requestedPath),
        cause);
  }
}
