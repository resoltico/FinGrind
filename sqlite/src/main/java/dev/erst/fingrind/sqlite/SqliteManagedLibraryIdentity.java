package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.LinkOption;
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
   * Returns a fresh, retained owner-only snapshot whose copied sibling checksum verifies the copied
   * library bytes.
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
    if (!Files.isRegularFile(sourceChecksumPath, LinkOption.NOFOLLOW_LINKS)) {
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
    if (!Files.isRegularFile(checksumPath, LinkOption.NOFOLLOW_LINKS)) {
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
    try {
      return SqliteManagedLibraryDigestSupport.expectedSha256(
          Files.readAllLines(checksumPath), checksumSourceDescription, expectedFileName);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to read the managed SQLite checksum file at " + checksumPath + ".", exception);
    }
  }

  static String expectedSha256(
      List<String> checksumLines, String checksumSourceDescription, String expectedFileName) {
    return SqliteManagedLibraryDigestSupport.expectedSha256(
        checksumLines, checksumSourceDescription, expectedFileName);
  }

  static String actualSha256(Path libraryPath) {
    return SqliteManagedLibraryDigestSupport.actualSha256(libraryPath);
  }

  static Path createPrivateSnapshotDirectory(Path tempRoot, boolean supportsPosix) {
    return SqliteManagedLibrarySnapshotSecurity.createPrivateSnapshotDirectory(
        tempRoot, supportsPosix);
  }
}
