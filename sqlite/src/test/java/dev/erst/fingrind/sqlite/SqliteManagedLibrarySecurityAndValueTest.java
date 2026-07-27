package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused tests for managed-library identity value-object rules. */
class SqliteManagedLibrarySecurityAndValueTest extends SqliteManagedLibraryIdentityTestSupport {
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
}
