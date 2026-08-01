package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Facade over the managed SQLite library identity contract and verified-snapshot workflow. */
final class SqliteManagedLibraryIdentity {
  private SqliteManagedLibraryIdentity() {}

  static void requireVerified(SqliteLibraryTarget libraryTarget) {
    verifiedSnapshot(libraryTarget);
  }

  /**
   * Returns a fresh owner-only snapshot whose copied sibling checksum verifies the copied library
   * bytes and which remains until the process-scoped native runtime closes.
   *
   * <p>A verification failure retains the incomplete snapshot attempt. The process must not delete
   * a pathname after another actor could have replaced it.
   */
  static SqliteVerifiedLibrarySnapshot verifiedSnapshot(SqliteLibraryTarget libraryTarget) {
    Objects.requireNonNull(libraryTarget, "libraryTarget");
    Path sourceLibraryPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(
            Path.of(libraryTarget.lookupTarget()));
    SqliteManagedLibraryDigestSupport.requireManagedLibrary(sourceLibraryPath);
    Path sourceChecksumPath = checksumPath(sourceLibraryPath);
    if (!isReadableNofollow(sourceChecksumPath)) {
      throw SqliteManagedLibraryDigestSupport.missingChecksumFile(
          sourceLibraryPath, sourceChecksumPath);
    }
    return SqliteVerifiedLibrarySnapshot.copyOf(
        libraryTarget, sourceLibraryPath, sourceChecksumPath);
  }

  static void requireSiblingVerified(Path libraryPath) {
    Path normalizedLibraryPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(libraryPath);
    SqliteManagedLibraryDigestSupport.requireManagedLibrary(normalizedLibraryPath);
    Path checksumPath = checksumPath(normalizedLibraryPath);
    if (!isReadableNofollow(checksumPath)) {
      throw SqliteManagedLibraryDigestSupport.missingChecksumFile(
          normalizedLibraryPath, checksumPath);
    }
    String expectedSha256 =
        expectedSha256(checksumPath, normalizedLibraryPath.getFileName().toString());
    String actualSha256 = actualSha256(normalizedLibraryPath);
    if (!actualSha256.equals(expectedSha256)) {
      throw SqliteManagedLibraryDigestSupport.mismatchedIdentity(
          normalizedLibraryPath,
          SqliteManagedLibraryDigestSupport.identitySourceDescription(checksumPath),
          expectedSha256,
          actualSha256);
    }
  }

  static Path checksumPath(Path libraryPath) {
    return SqliteManagedLibraryDigestSupport.checksumPath(libraryPath);
  }

  static String expectedSha256(Path checksumPath, String expectedFileName) {
    return SqliteManagedLibraryDigestSupport.expectedSha256(checksumPath, expectedFileName);
  }

  static String expectedSha256(
      Path checksumPath, String checksumSourceDescription, String expectedFileName) {
    return SqliteManagedLibraryDigestSupport.expectedSha256(
        checksumPath, checksumSourceDescription, expectedFileName);
  }

  static String expectedSha256(
      List<String> checksumLines, String checksumSourceDescription, String expectedFileName) {
    return SqliteManagedLibraryDigestSupport.expectedSha256(
        checksumLines, checksumSourceDescription, expectedFileName);
  }

  static String actualSha256(Path libraryPath) {
    return SqliteManagedLibraryDigestSupport.actualSha256(libraryPath);
  }

  static Path createPrivateSnapshotDirectory(
      Path tempRoot,
      SqliteManagedLibrarySnapshotSecurity.SnapshotDirectoryCreator directoryCreator) {
    return SqliteManagedLibrarySnapshotSecurity.createPrivateSnapshotDirectory(
        tempRoot, directoryCreator);
  }

  private static boolean isReadableNofollow(Path path) {
    try {
      SqliteNofollowFileAccess.requireReadableRegularFile(path);
      return true;
    } catch (java.io.IOException exception) {
      return false;
    }
  }
}
