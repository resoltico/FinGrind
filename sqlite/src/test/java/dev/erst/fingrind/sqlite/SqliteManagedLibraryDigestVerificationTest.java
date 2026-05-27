package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for managed-library digest verification contracts. */
class SqliteManagedLibraryDigestVerificationTest extends SqliteManagedLibraryIdentityTestSupport {
  @Test
  void requireSiblingVerified_acceptsMatchingSiblingChecksumFiles() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    assertDoesNotThrow(() -> SqliteManagedLibraryIdentity.requireSiblingVerified(libraryPath));
  }

  @Test
  void requireVerified_rejectsManagedLibraryWithoutTrustedChecksum() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.requireVerified(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString())));
    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("trusted FinGrind digest file"));
  }

  @Test
  void requireVerified_acceptsMatchingBundleManagedSiblingAndTrustedDigests() throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);
    writeTrustedChecksum(libraryPath);

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
    writeTrustedChecksum(libraryPath);

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
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString())));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("missing the sibling SHA-256 file"));
  }

  @Test
  void verifiedSnapshot_rejectsMissingTrustedChecksumBeforeCreatingCopy() throws Exception {
    Path libraryPath = writeLibrary(hostManagedLibraryFileName(), "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteManagedLibraryIdentity.verifiedSnapshot(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString())));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("missing the trusted FinGrind digest file"));
  }

  @Test
  void requireVerified_rejectsManagedRuntimeWhenTrustedDigestDoesNotMatch() throws Exception {
    Path libraryPath = writeLibrary(hostManagedLibraryFileName(), "tampered-library");
    writeSiblingChecksum(libraryPath);
    writeTrustedChecksum(libraryPath, hostManagedLibraryPath());

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
    writeTrustedChecksum(libraryPath);

    assertDoesNotThrow(
        () -> SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(libraryPath));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsMalformedTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Files.writeString(
        SqliteManagedLibraryIdentity.trustedChecksumPath(libraryPath),
        "not-a-checksum\n",
        StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(libraryPath));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is malformed"));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsMissingTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(libraryPath));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("is missing the trusted FinGrind digest file"));
  }

  @Test
  void requireTrustedManagedLibrary_rejectsBlankTrustedDigestText() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Files.writeString(
        SqliteManagedLibraryIdentity.trustedChecksumPath(libraryPath),
        " \n",
        StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(libraryPath));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("is empty"));
  }

  @Test
  void digestHelperWrappers_delegateToTheDigestSupportOwner() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    String expectedDigest = SqliteManagedLibraryIdentity.actualSha256(libraryPath);

    assertEquals(
        expectedDigest,
        SqliteManagedLibraryIdentity.expectedSha256(
            java.util.List.of(sha256Line(libraryPath, "libsqlite3.so.0")),
            "test digest",
            "libsqlite3.so.0"));
    assertEquals(expectedDigest, SqliteManagedLibraryIdentity.actualSha256(libraryPath, "SHA-256"));
    assertEquals("SHA-256", SqliteManagedLibraryIdentity.sha256Digest("SHA-256").getAlgorithm());
  }

  @Test
  void requireTrustedManagedLibrary_wrapsTrustedDigestReadIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath libraryPath = fileSystem.path("\\sqlite3.dll");
      libraryPath.exists = true;
      libraryPath.regularFile = true;
      AclFixturePath trustedChecksumPath =
          (AclFixturePath) SqliteManagedLibraryIdentity.trustedChecksumPath(libraryPath);
      trustedChecksumPath.exists = true;
      trustedChecksumPath.regularFile = true;
      trustedChecksumPath.failNewByteChannelWith(new java.io.IOException("trusted-read-failure"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteManagedLibraryIdentity.requireTrustedManagedLibrary(libraryPath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to read the managed SQLite checksum file"));
      assertEquals(
          "trusted-read-failure", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void trustedChecksumPath_appendsTrustedDigestSuffixBesideLibrary() {
    Path libraryPath = tempDirectory.resolve("libsqlite3.so.0");

    assertEquals(
        tempDirectory.resolve("libsqlite3.so.0.trusted.sha256"),
        SqliteManagedLibraryIdentity.trustedChecksumPath(libraryPath));
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
                    java.util.List.of("not-a-checksum"), "", "libsqlite3.so.0"));

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
}
