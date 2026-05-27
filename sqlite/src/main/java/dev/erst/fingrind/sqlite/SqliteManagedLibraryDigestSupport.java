package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Digest parsing and hashing support for the managed SQLite library contract. */
final class SqliteManagedLibraryDigestSupport {
  private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-fA-F]{64})\\s+\\*?(.+)$");
  private static final String TRUSTED_CHECKSUM_SUFFIX = ".trusted.sha256";

  private SqliteManagedLibraryDigestSupport() {}

  static Path normalizedLibraryPath(Path libraryPath) {
    Objects.requireNonNull(libraryPath, "libraryPath");
    return libraryPath.toAbsolutePath().normalize();
  }

  static void requireManagedLibrary(Path libraryPath) {
    if (!Files.isRegularFile(libraryPath)) {
      throw missingManagedLibrary(libraryPath);
    }
  }

  static Path checksumPath(Path libraryPath) {
    Path normalizedLibraryPath = normalizedLibraryPath(libraryPath);
    return normalizedLibraryPath.resolveSibling(normalizedLibraryPath.getFileName() + ".sha256");
  }

  static Path trustedChecksumPath(Path libraryPath) {
    Path normalizedLibraryPath = normalizedLibraryPath(libraryPath);
    return normalizedLibraryPath.resolveSibling(
        normalizedLibraryPath.getFileName() + TRUSTED_CHECKSUM_SUFFIX);
  }

  static String expectedSha256(Path checksumPath, String expectedFileName) {
    try {
      return expectedSha256(
          Files.readAllLines(checksumPath),
          "managed SQLite checksum file at " + checksumPath,
          expectedFileName);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the managed SQLite checksum file at " + checksumPath + ".", exception);
    }
  }

  static String expectedSha256(
      List<String> checksumLines, String checksumSourceDescription, String expectedFileName) {
    Objects.requireNonNull(checksumLines, "checksumLines");
    Objects.requireNonNull(checksumSourceDescription, "checksumSourceDescription");
    Objects.requireNonNull(expectedFileName, "expectedFileName");
    String checksumLine =
        checksumLines.stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        capitalize(checksumSourceDescription) + " is empty."));
    Matcher matcher = CHECKSUM_LINE.matcher(checksumLine);
    if (!matcher.matches()) {
      throw new IllegalStateException(capitalize(checksumSourceDescription) + " is malformed.");
    }
    String declaredFileName = matcher.group(2).strip();
    if (!expectedFileName.equals(declaredFileName)) {
      throw new IllegalStateException(
          capitalize(checksumSourceDescription)
              + " targets "
              + declaredFileName
              + " instead of "
              + expectedFileName
              + ".");
    }
    return matcher.group(1).toLowerCase(Locale.ROOT);
  }

  static String actualSha256(Path libraryPath) {
    return actualSha256(libraryPath, "SHA-256");
  }

  static String actualSha256(Path libraryPath, String algorithm) {
    MessageDigest digest = sha256Digest(algorithm);
    try (InputStream inputStream = Files.newInputStream(libraryPath)) {
      byte[] buffer = new byte[16 * 1024];
      while (true) {
        int read = inputStream.read(buffer);
        if (read < 0) {
          break;
        }
        digest.update(buffer, 0, read);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the managed SQLite library at "
              + libraryPath
              + " for SHA-256 verification.",
          exception);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static MessageDigest sha256Digest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          algorithm + " is unavailable in the active Java runtime.", exception);
    }
  }

  static String trustedIdentitySourceDescription(Path trustedChecksumPath) {
    return "trusted FinGrind managed SQLite digest file at "
        + trustedChecksumPath.toAbsolutePath().normalize();
  }

  static String identitySourceDescription(Path checksumPath) {
    return "sibling SHA-256 file " + checksumPath.toAbsolutePath().normalize();
  }

  static IllegalStateException missingManagedLibrary(Path libraryPath) {
    return new IllegalStateException(
        "Managed SQLite library does not exist at "
            + libraryPath
            + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.");
  }

  static IllegalStateException missingChecksumFile(Path libraryPath, Path checksumPath) {
    return new IllegalStateException(
        "Managed SQLite library at "
            + libraryPath
            + " is missing the sibling SHA-256 file "
            + checksumPath
            + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.");
  }

  static IllegalStateException missingTrustedChecksumFile(
      Path libraryPath, Path trustedChecksumPath) {
    return new IllegalStateException(
        "Managed SQLite library at "
            + libraryPath
            + " is missing the trusted FinGrind digest file "
            + trustedChecksumPath
            + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.");
  }

  static UnsupportedManagedSqliteLibraryIdentityException mismatchedIdentity(
      Path libraryPath, String identitySource, String expectedSha256, String actualSha256) {
    return new UnsupportedManagedSqliteLibraryIdentityException(
        libraryPath, identitySource, expectedSha256, actualSha256);
  }

  private static String capitalize(String text) {
    if (text.isEmpty()) {
      return text;
    }
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
