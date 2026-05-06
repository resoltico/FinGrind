package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for managed SQLite library identity enforcement. */
class SqliteManagedLibraryIdentityTest {
  @TempDir Path tempDirectory;

  @Test
  void requireSiblingVerified_acceptsMatchingSiblingChecksumFiles() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    assertDoesNotThrow(() -> SqliteManagedLibraryIdentity.requireSiblingVerified(libraryPath));
  }

  @Test
  void requireVerified_acceptsMatchingEnvironmentConfiguredSiblingChecksum() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    assertDoesNotThrow(
        () ->
            SqliteManagedLibraryIdentity.requireVerified(
                new SqliteLibraryTarget(
                    "managed-only",
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    libraryPath.toString())));
  }

  @Test
  void requireVerified_acceptsMatchingBundleManagedSiblingAndTrustedDigests() throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);

    assertDoesNotThrow(
        () ->
            SqliteManagedLibraryIdentity.requireVerified(
                new SqliteLibraryTarget(
                    "managed-only",
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    libraryPath.toString())));
  }

  @Test
  void requireSiblingVerified_rejectsMissingSiblingChecksumFiles() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.requireSiblingVerified(libraryPath));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("missing the sibling SHA-256 file"));
  }

  @Test
  void requireSiblingVerified_rejectsMismatchedSiblingChecksumFiles() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Files.writeString(
        SqliteManagedLibraryIdentity.checksumPath(libraryPath),
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  libsqlite3.so.0\n",
        StandardCharsets.UTF_8);

    UnsupportedManagedSqliteLibraryIdentityException exception =
        assertThrows(
            UnsupportedManagedSqliteLibraryIdentityException.class,
            () -> SqliteManagedLibraryIdentity.requireSiblingVerified(libraryPath));

    assertEquals(libraryPath.toAbsolutePath().normalize(), exception.libraryPath());
    assertTrue(exception.identitySource().contains(".sha256"));
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("did not match the expected SHA-256"));
  }

  @Test
  void requireVerified_acceptsSourceCheckoutManagedLibraryWhenTrustedAndSiblingDigestsMatch()
      throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);

    assertDoesNotThrow(
        () ->
            SqliteManagedLibraryIdentity.requireVerified(
                new SqliteLibraryTarget(
                    "managed-only",
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    libraryPath.toString())));
  }

  @Test
  void verifiedSnapshot_rejectsMissingChecksumBeforeCreatingCopy() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.verifiedSnapshot(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                        libraryPath.toString())));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("missing the sibling SHA-256 file"));
  }

  @Test
  void requireVerified_rejectsManagedRuntimeWhenTrustedDigestDoesNotMatch() throws Exception {
    Path libraryPath = writeLibrary(hostManagedLibraryFileName(), "tampered-library");
    writeSiblingChecksum(libraryPath);

    UnsupportedManagedSqliteLibraryIdentityException exception =
        assertThrows(
            UnsupportedManagedSqliteLibraryIdentityException.class,
            () ->
                SqliteManagedLibraryIdentity.requireVerified(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString())));

    assertTrue(exception.identitySource().contains("trusted FinGrind managed SQLite digest"));
  }

  @Test
  void requireTrustedManagedLibrary_acceptsMatchingTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    assertDoesNotThrow(
        () ->
            SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(
                libraryPath,
                ignoredResourcePath ->
                    sha256Line(libraryPath, libraryPath.getFileName().toString())));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsMalformedTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(
                    libraryPath, ignoredResourcePath -> "not-a-checksum\n"));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is malformed"));
  }

  @Test
  void requireTrustedManagedLibrary_wrapsTrustedDigestReadIoFailures() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(
                    libraryPath,
                    ignoredResourcePath -> {
                      throw new IOException("boom");
                    }));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to read the trusted FinGrind managed SQLite digest resource"));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsMissingTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(
                    libraryPath, ignoredResourcePath -> null));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is missing or empty"));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsBlankTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(
                    libraryPath, ignoredResourcePath -> " \n"));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is missing or empty"));
  }

  @Test
  void trustedChecksumText_readsBundledDigestResource() throws Exception {
    String checksumText =
        SqliteManagedLibraryIdentity.trustedChecksumText(
            "/META-INF/fingrind/managed-sqlite.sha256");

    assertEquals(expectedTrustedChecksumText(), checksumText);
  }

  @Test
  void verifiedSnapshot_copiesManagedLibraryIntoPrivateVerifiedPath() throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);

    SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot snapshot =
        SqliteManagedLibraryIdentity.verifiedSnapshot(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()));

    assertTrue(snapshot.snapshotLibraryPath().startsWith(snapshot.snapshotDirectory()));
    assertTrue(snapshot.snapshotChecksumPath().startsWith(snapshot.snapshotDirectory()));
    assertTrue(Files.isRegularFile(snapshot.snapshotLibraryPath()));
    assertTrue(Files.isRegularFile(snapshot.snapshotChecksumPath()));
    assertArrayEquals(
        Files.readAllBytes(libraryPath), Files.readAllBytes(snapshot.snapshotLibraryPath()));
    assertEquals(
        Files.readString(
            SqliteManagedLibraryIdentity.checksumPath(libraryPath), StandardCharsets.UTF_8),
        Files.readString(snapshot.snapshotChecksumPath(), StandardCharsets.UTF_8));
    assertEquals(
        snapshot.snapshotLibraryPath().toString(), snapshot.runtimeTarget().lookupTarget());

    snapshot.deleteQuietly();
  }

  @Test
  void verifiedSnapshot_rejectsUnusableSnapshotTempRoot() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);
    String originalTempRoot = System.getProperty("java.io.tmpdir");
    String missingTempRoot = tempDirectory.resolve("missing-temp-root").toString();
    System.setProperty("java.io.tmpdir", missingTempRoot);
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                          libraryPath.toString()),
                      libraryPath,
                      SqliteManagedLibraryIdentity.checksumPath(libraryPath)));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "Failed to create a private managed SQLite verification snapshot directory."));
    } finally {
      System.setProperty("java.io.tmpdir", originalTempRoot);
    }
  }

  @Test
  void createPrivateSnapshotDirectory_supportsNonPosixFallbackCreation() throws Exception {
    Path snapshotDirectory =
        SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(tempDirectory, false);

    assertTrue(snapshotDirectory.startsWith(tempDirectory));
    assertTrue(Files.isDirectory(snapshotDirectory));
    Files.delete(snapshotDirectory);
  }

  @Test
  void verifiedSnapshot_recordRejectsSnapshotPathsOutsideSnapshotDirectory() throws Exception {
    Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve("snapshot"));
    Path outsideLibraryPath = tempDirectory.resolve("outside-library.dylib");
    Path outsideChecksumPath = tempDirectory.resolve("outside-library.dylib.sha256");
    Path validLibraryPath = snapshotDirectory.resolve("library.dylib");
    Path validChecksumPath = snapshotDirectory.resolve("library.dylib.sha256");
    Files.writeString(outsideLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(outsideChecksumPath, "checksum", StandardCharsets.UTF_8);
    Files.writeString(validLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(validChecksumPath, "checksum", StandardCharsets.UTF_8);
    SqliteLibraryTarget sourceTarget =
        new SqliteLibraryTarget(
            "managed-only",
            SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
            validLibraryPath.toString());

    IllegalArgumentException outsideLibrary =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot(
                    sourceTarget, snapshotDirectory, outsideLibraryPath, validChecksumPath));
    IllegalArgumentException outsideChecksum =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot(
                    sourceTarget, snapshotDirectory, validLibraryPath, outsideChecksumPath));

    assertEquals(
        "snapshotLibraryPath must live inside snapshotDirectory.", outsideLibrary.getMessage());
    assertEquals(
        "snapshotChecksumPath must live inside snapshotDirectory.", outsideChecksum.getMessage());
  }

  @Test
  void verifiedSnapshot_copyOfCleansUpAfterCopyFailures() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Path missingChecksumPath = tempDirectory.resolve("missing.sha256");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot.copyOf(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                        libraryPath.toString()),
                    libraryPath,
                    missingChecksumPath));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to create the private managed SQLite verification snapshot"));
  }

  @Test
  void hardenPrivateFile_returnsQuietlyWhenFileStoreMetadataCannotBeResolved() throws Exception {
    Path missingPath = tempDirectory.resolve("missing-parent").resolve("missing.dylib");

    assertDoesNotThrow(() -> SqliteManagedLibraryIdentity.hardenPrivateFile(missingPath));
  }

  @Test
  void hardenPrivateFile_wrapsPermissionFailures() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.hardenPrivateFile(Path.of("/dev/null")));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to apply private managed SQLite snapshot permissions"));
  }

  @Test
  void verifiedSnapshot_deleteQuietly_ignoresDirectoryDeleteFailures() throws Exception {
    Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve("snapshot-cleanup"));
    Path snapshotLibraryPath = snapshotDirectory.resolve("library.dylib");
    Path snapshotChecksumPath = snapshotDirectory.resolve("library.dylib.sha256");
    Path sentinel = snapshotDirectory.resolve("sentinel.txt");
    Files.writeString(snapshotLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(snapshotChecksumPath, "checksum", StandardCharsets.UTF_8);
    Files.writeString(sentinel, "keep", StandardCharsets.UTF_8);
    SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot snapshot =
        new SqliteManagedLibraryIdentity.VerifiedLibrarySnapshot(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                snapshotLibraryPath.toString()),
            snapshotDirectory,
            snapshotLibraryPath,
            snapshotChecksumPath);

    assertDoesNotThrow(snapshot::deleteQuietly);
    assertTrue(Files.exists(snapshotDirectory));
    assertTrue(Files.exists(sentinel));
    assertTrue(Files.notExists(snapshotLibraryPath));
    assertTrue(Files.notExists(snapshotChecksumPath));
  }

  @Test
  void trustedChecksumText_returnsNullForMissingResources() throws Exception {
    assertEquals(
        null,
        SqliteManagedLibraryIdentity.trustedChecksumText("/META-INF/fingrind/missing.sha256"));
  }

  @Test
  void trustedChecksumText_propagatesCloseFailuresFromProvidedStreams() {
    InputStream failingStream =
        new ByteArrayInputStream("digest".getBytes(StandardCharsets.UTF_8)) {
          @Override
          public void close() throws IOException {
            throw new IOException("close failure");
          }
        };

    IOException exception =
        assertThrows(
            IOException.class,
            () -> SqliteManagedLibraryIdentity.trustedChecksumText(failingStream));

    assertEquals("close failure", exception.getMessage());
  }

  @Test
  void expectedSha256_ignoresBlankLinesBeforeFirstChecksumLine() throws Exception {
    Path libraryPath = writeLibrary("blank-lines.dylib", "sqlite3mc");
    Path checksumPath = SqliteManagedLibraryIdentity.checksumPath(libraryPath);
    Files.writeString(
        checksumPath,
        "\n  \n" + sha256Line(libraryPath, libraryPath.getFileName().toString()),
        StandardCharsets.UTF_8);

    assertEquals(
        SqliteManagedLibraryIdentity.actualSha256(libraryPath),
        SqliteManagedLibraryIdentity.expectedSha256(
            checksumPath, libraryPath.getFileName().toString()));
  }

  @Test
  void expectedSha256_rejectsEmptyChecksumFiles() throws Exception {
    Path libraryPath = writeLibrary("empty-checksum.dylib", "sqlite3mc");
    Path checksumPath = SqliteManagedLibraryIdentity.checksumPath(libraryPath);
    Files.writeString(checksumPath, "\n \n", StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.expectedSha256(
                    checksumPath, libraryPath.getFileName().toString()));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is empty"));
  }

  @Test
  void expectedSha256_rejectsChecksumFilesForDifferentTargets() throws Exception {
    Path libraryPath = writeLibrary("wrong-target.dylib", "sqlite3mc");
    Path checksumPath = SqliteManagedLibraryIdentity.checksumPath(libraryPath);
    Files.writeString(
        checksumPath, sha256Line(libraryPath, "different-target.dylib"), StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.expectedSha256(
                    checksumPath, libraryPath.getFileName().toString()));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("instead of"));
  }

  @Test
  void expectedSha256_handlesEmptySourceDescriptionsWhenReportingMalformedChecksums() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.expectedSha256(
                    List.of("not-a-checksum"), "", "libsqlite3.so.0"));

    assertEquals(" is malformed.", exception.getMessage());
  }

  @Test
  void expectedSha256_wrapsChecksumReadIoFailures() {
    Path checksumPath = tempDirectory.resolve("missing.sha256");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.expectedSha256(checksumPath, "libsqlite3.dylib"));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to read the managed SQLite checksum file"));
  }

  @Test
  void actualSha256_wrapsLibraryReadIoFailures() {
    Path libraryPath = tempDirectory.resolve("missing-library.dylib");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.actualSha256(libraryPath, "SHA-256"));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to read the managed SQLite library"));
  }

  @Test
  void sha256Digest_rejectsUnavailableAlgorithms() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.sha256Digest("definitely-not-a-real-algorithm"));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("definitely-not-a-real-algorithm is unavailable"));
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_normalizesPathsAndDigests() {
    Path libraryPath = tempDirectory.resolve("library").resolve("..").resolve("libsqlite3.dylib");
    UnsupportedManagedSqliteLibraryIdentityException exception =
        new UnsupportedManagedSqliteLibraryIdentityException(
            libraryPath,
            " trusted resource ",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

    assertEquals(libraryPath.toAbsolutePath().normalize(), exception.libraryPath());
    assertEquals("trusted resource", exception.identitySource());
    assertEquals(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        exception.expectedSha256());
    assertEquals(
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        exception.actualSha256());
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_rejectsInvalidDigests() {
    Path libraryPath = tempDirectory.resolve("libsqlite3.dylib");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new UnsupportedManagedSqliteLibraryIdentityException(
                    libraryPath,
                    "trusted resource",
                    "invalid",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("expectedSha256 must be one 64-character lowercase SHA-256 digest"));
  }

  @Test
  void unsupportedManagedSqliteLibraryIdentityException_rejectsBlankIdentitySources() {
    Path libraryPath = tempDirectory.resolve("libsqlite3.dylib");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new UnsupportedManagedSqliteLibraryIdentityException(
                    libraryPath,
                    "   ",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    assertEquals("identitySource must not be blank.", exception.getMessage());
  }

  private Path writeLibrary(String fileName, String contents) throws IOException {
    Path libraryPath = tempDirectory.resolve(fileName);
    Files.writeString(libraryPath, contents, StandardCharsets.UTF_8);
    return libraryPath;
  }

  private Path copyHostManagedLibrary() throws IOException {
    Path sourceLibraryPath = hostManagedLibraryPath();
    Path copiedLibraryPath = tempDirectory.resolve(sourceLibraryPath.getFileName().toString());
    Files.copy(sourceLibraryPath, copiedLibraryPath);
    return copiedLibraryPath;
  }

  private void writeSiblingChecksum(Path libraryPath) throws IOException {
    Files.writeString(
        SqliteManagedLibraryIdentity.checksumPath(libraryPath),
        sha256Line(libraryPath, libraryPath.getFileName().toString()),
        StandardCharsets.UTF_8);
  }

  private static String sha256Line(Path libraryPath, String declaredFileName) {
    return SqliteManagedLibraryIdentity.actualSha256(libraryPath) + "  " + declaredFileName + "\n";
  }

  private static String hostManagedLibraryFileName() {
    return hostManagedLibraryPath().getFileName().toString();
  }

  private static Path hostManagedLibraryPath() {
    String configuredLibraryPath = System.getenv(SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE);
    if (configuredLibraryPath == null || configuredLibraryPath.isBlank()) {
      throw new IllegalStateException(
          "Missing " + SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE + " for managed SQLite tests.");
    }
    return Path.of(configuredLibraryPath);
  }

  private static String expectedTrustedChecksumText() {
    Path libraryPath = hostManagedLibraryPath();
    return sha256Line(libraryPath, libraryPath.getFileName().toString());
  }
}
