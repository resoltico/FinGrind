package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Reports a managed SQLite library whose binary identity no longer matches FinGrind's contract. */
public final class UnsupportedManagedSqliteLibraryIdentityException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final Path libraryPath;
  private final String identitySource;
  private final String expectedSha256;
  private final String actualSha256;

  UnsupportedManagedSqliteLibraryIdentityException(
      Path libraryPath, String identitySource, String expectedSha256, String actualSha256) {
    super(message(libraryPath, identitySource, expectedSha256, actualSha256));
    this.libraryPath = requirePath(libraryPath, "libraryPath");
    this.identitySource = requireIdentitySource(identitySource);
    this.expectedSha256 = requireSha256(expectedSha256, "expectedSha256");
    this.actualSha256 = requireSha256(actualSha256, "actualSha256");
  }

  public Path libraryPath() {
    return libraryPath;
  }

  public String identitySource() {
    return identitySource;
  }

  public String expectedSha256() {
    return expectedSha256;
  }

  public String actualSha256() {
    return actualSha256;
  }

  private static String message(
      Path libraryPath, String identitySource, String expectedSha256, String actualSha256) {
    return "Managed SQLite library at "
        + requirePath(libraryPath, "libraryPath")
        + " did not match the expected SHA-256 from "
        + requireIdentitySource(identitySource)
        + ". Expected "
        + requireSha256(expectedSha256, "expectedSha256")
        + " but found "
        + requireSha256(actualSha256, "actualSha256")
        + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.";
  }

  private static Path requirePath(Path path, String fieldName) {
    Objects.requireNonNull(path, fieldName);
    return path.toAbsolutePath().normalize();
  }

  private static String requireIdentitySource(String identitySource) {
    Objects.requireNonNull(identitySource, "identitySource");
    String normalized = identitySource.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("identitySource must not be blank.");
    }
    return normalized;
  }

  private static String requireSha256(String sha256, String fieldName) {
    Objects.requireNonNull(sha256, fieldName);
    String normalized = sha256.trim().toLowerCase(Locale.ROOT);
    if (!normalized.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          fieldName + " must be one 64-character lowercase SHA-256 digest.");
    }
    return normalized;
  }
}
