package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable private verification copy of one selected managed SQLite library and checksum.
 *
 * <p>Every copy lives in a fresh owner-only directory and remains retained while the native-library
 * arena can resolve it. Snapshot creation has no pathname cleanup or replacement authority: a later
 * actor can replace a name after this process creates it, so deletion or overwrite during
 * verification would violate the verified-library trust boundary. After that arena closes, the
 * runtime removes only the exact verified library, checksum, and now-empty private directory.
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
        PrivateOutputFile.OpenedFile snapshotLibrary =
            SqliteManagedLibrarySnapshotSecurity.openNewPrivateSnapshotChannel(
                snapshotLibraryPath);
        FileChannel sourceChecksum =
            SqliteManagedLibrarySnapshotSecurity.openSourceReadChannel(checkedSourceChecksumPath);
        PrivateOutputFile.OpenedFile snapshotChecksum =
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

  /**
   * Releases this fully verified snapshot after its native-library arena has closed.
   *
   * <p>This deliberately does not recursively remove the directory: a changed namespace must retain
   * every unexpected entry as evidence rather than allowing the runtime to delete it.
   */
  void releaseAfterNativeRuntimeClose() {
    if (Files.notExists(snapshotDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try {
      PrivateOutputDirectory.requireExistingOwnerOnly(snapshotDirectory);
      requireCurrentArtifactsMatchVerifiedSnapshot();
      Files.deleteIfExists(snapshotLibraryPath);
      Files.deleteIfExists(snapshotChecksumPath);
      Files.deleteIfExists(snapshotDirectory);
    } catch (IOException | RuntimeException exception) {
      SqliteBestEffort.reportRetainedEvidenceReleaseFailure(
          "releasing one verified managed SQLite runtime snapshot", exception);
    }
  }

  /** Re-admits and revalidates both artifacts before their private namespace is released. */
  private void requireCurrentArtifactsMatchVerifiedSnapshot() throws IOException {
    try (PrivateOutputFile.OpenedFile snapshotLibrary =
            PrivateOutputFile.openExisting(
                snapshotLibraryPath, PrivateOutputFile.Access.READ_ONLY);
        PrivateOutputFile.OpenedFile snapshotChecksum =
            PrivateOutputFile.openExisting(
                snapshotChecksumPath, PrivateOutputFile.Access.READ_ONLY)) {
      snapshotLibrary.position(0L);
      String actualLibrarySha256 =
          CryptographicPrimitives.sha256Hex(Channels.newInputStream(snapshotLibrary));
      if (!snapshotLibrarySha256.equals(actualLibrarySha256)) {
        throw new IOException(
            "The verified managed SQLite library snapshot changed before its private namespace could be released.");
      }
      String declaredLibrarySha256 =
          SqliteManagedLibraryDigestSupport.expectedSha256(
              SqliteManagedLibrarySnapshotSecurity.readUtf8LinesFromExactChannel(snapshotChecksum),
              "private managed SQLite checksum snapshot at " + snapshotChecksumPath,
              snapshotLibraryPath.getFileName().toString());
      if (!snapshotLibrarySha256.equals(declaredLibrarySha256)) {
        throw new IOException(
            "The verified managed SQLite checksum snapshot changed before its private namespace could be released.");
      }
    }
  }
}
