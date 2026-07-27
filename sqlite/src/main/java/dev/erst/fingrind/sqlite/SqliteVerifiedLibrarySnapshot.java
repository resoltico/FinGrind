package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable private verification copy of one selected managed SQLite library and checksum.
 *
 * <p>Every copy lives in a fresh owner-only directory and is deliberately retained. Snapshot
 * creation has no pathname cleanup or replacement authority: a later actor can replace a name after
 * this process creates it, so deletion or overwrite would violate the verified-library trust
 * boundary.
 */
record SqliteVerifiedLibrarySnapshot(
    SqliteLibraryTarget sourceTarget,
    Path snapshotDirectory,
    Path snapshotLibraryPath,
    Path snapshotChecksumPath,
    String snapshotLibrarySha256) {
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
    Objects.requireNonNull(snapshotLibrarySha256, "snapshotLibrarySha256");
    if (!snapshotLibrarySha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "snapshotLibrarySha256 must be one lowercase 64-character SHA-256 digest.");
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
    Path checkedSourceLibraryPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(sourceLibraryPath);
    Path checkedSourceChecksumPath =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(sourceChecksumPath);
    Path snapshotDirectory =
        requirePrivateSnapshotDirectory(
            Objects.requireNonNull(snapshotDirectoryFactory, "snapshotDirectoryFactory").get());
    Path snapshotLibraryPath = snapshotDirectory.resolve(checkedSourceLibraryPath.getFileName());
    Path snapshotChecksumPath = snapshotDirectory.resolve(checkedSourceChecksumPath.getFileName());
    String actualSha256;
    try (FileChannel sourceLibrary =
            SqliteManagedLibrarySnapshotSecurity.openSourceReadChannel(checkedSourceLibraryPath);
        FileChannel snapshotLibrary =
            SqliteManagedLibrarySnapshotSecurity.openNewPrivateSnapshotChannel(
                snapshotLibraryPath);
        FileChannel sourceChecksum =
            SqliteManagedLibrarySnapshotSecurity.openSourceReadChannel(checkedSourceChecksumPath);
        FileChannel snapshotChecksum =
            SqliteManagedLibrarySnapshotSecurity.openNewPrivateSnapshotChannel(
                snapshotChecksumPath)) {
      actualSha256 =
          SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
              sourceLibrary, snapshotLibrary);
      SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
          sourceChecksum, snapshotChecksum);
      String expectedSha256 =
          SqliteManagedLibraryDigestSupport.expectedSha256(
              SqliteManagedLibrarySnapshotSecurity.readUtf8LinesFromExactChannel(snapshotChecksum),
              "private managed SQLite checksum snapshot at " + snapshotChecksumPath,
              checkedSourceLibraryPath.getFileName().toString());
      if (!actualSha256.equals(expectedSha256)) {
        throw SqliteManagedLibraryDigestSupport.mismatchedIdentity(
            snapshotLibraryPath,
            "private managed SQLite checksum snapshot at " + snapshotChecksumPath,
            expectedSha256,
            actualSha256);
      }
    } catch (IOException exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not create a retained private managed SQLite verification snapshot from "
              + checkedSourceLibraryPath
              + ".",
          exception);
    }
    return new SqliteVerifiedLibrarySnapshot(
        sourceTarget, snapshotDirectory, snapshotLibraryPath, snapshotChecksumPath, actualSha256);
  }

  /**
   * Rechecks the current snapshot pathname immediately before Java FFM receives that pathname.
   *
   * <p>This is defense in depth, not an exact loaded-image proof: Java FFM accepts a pathname, not
   * the nofollow descriptor used here, so a same-owner replacement remains possible between this
   * check and the platform loader's later pathname resolution.
   */
  void requireCurrentBytesMatchVerifiedDigestBeforePathLoad() {
    final String actualSha256;
    try (FileChannel libraryChannel =
            SqliteManagedLibrarySnapshotSecurity.openSourceReadChannel(snapshotLibraryPath);
        InputStream libraryInput = Channels.newInputStream(libraryChannel)) {
      actualSha256 = CryptographicPrimitives.sha256Hex(libraryInput);
    } catch (IOException exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not revalidate the retained managed SQLite snapshot immediately before native loading at "
              + snapshotLibraryPath
              + ".",
          exception);
    }
    if (!snapshotLibrarySha256.equals(actualSha256)) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "The retained managed SQLite snapshot changed after verification and before native loading at "
              + snapshotLibraryPath
              + ".");
    }
  }

  private static Path requirePrivateSnapshotDirectory(Path suppliedSnapshotDirectory) {
    Path snapshotDirectory =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(
            Objects.requireNonNull(suppliedSnapshotDirectory, "snapshotDirectoryFactory result"));
    try {
      PrivateOutputDirectory.requireExistingOwnerOnly(snapshotDirectory);
      return snapshotDirectory;
    } catch (PrivateOutputDirectory.Violation exception) {
      throw new ManagedSqliteRuntimeUnavailableException(
          "FinGrind could not establish an exact private managed SQLite verification snapshot directory at "
              + snapshotDirectory
              + ".",
          exception);
    }
  }

  SqliteLibraryTarget runtimeTarget() {
    return new SqliteLibraryTarget(
        sourceTarget.mode(), sourceTarget.provenance(), snapshotLibraryPath.toString());
  }
}
