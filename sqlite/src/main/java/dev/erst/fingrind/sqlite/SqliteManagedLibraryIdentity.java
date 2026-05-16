package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Verifies one managed SQLite shared library against FinGrind's runtime-identity contract. */
final class SqliteManagedLibraryIdentity {
  private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-fA-F]{64})\\s+\\*?(.+)$");
  private static final String TRUSTED_IDENTITY_RESOURCE =
      "/META-INF/fingrind/managed-sqlite.sha256";
  private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private SqliteManagedLibraryIdentity() {}

  static void requireVerified(SqliteLibraryTarget libraryTarget) {
    VerifiedLibrarySnapshot verifiedLibrarySnapshot = verifiedSnapshot(libraryTarget);
    verifiedLibrarySnapshot.deleteQuietly();
  }

  static VerifiedLibrarySnapshot verifiedSnapshot(SqliteLibraryTarget libraryTarget) {
    Objects.requireNonNull(libraryTarget, "libraryTarget");
    Path sourceLibraryPath = normalizedLibraryPath(Path.of(libraryTarget.lookupTarget()));
    requireManagedLibrary(sourceLibraryPath);
    Path sourceChecksumPath = checksumPath(sourceLibraryPath);
    if (!Files.isRegularFile(sourceChecksumPath)) {
      throw missingChecksumFile(sourceLibraryPath, sourceChecksumPath);
    }
    VerifiedLibrarySnapshot verifiedLibrarySnapshot =
        VerifiedLibrarySnapshot.copyOf(libraryTarget, sourceLibraryPath, sourceChecksumPath);
    try {
      if (libraryTarget.provenance() == SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED) {
        requireSiblingVerified(verifiedLibrarySnapshot.snapshotLibraryPath());
      } else {
        requireTrustedManagedLibrary(verifiedLibrarySnapshot.snapshotLibraryPath());
        requireSiblingVerified(verifiedLibrarySnapshot.snapshotLibraryPath());
      }
      return verifiedLibrarySnapshot;
    } catch (RuntimeException | Error exception) {
      verifiedLibrarySnapshot.deleteQuietly();
      throw exception;
    }
  }

  static void requireSiblingVerified(Path libraryPath) {
    Path normalizedLibraryPath = normalizedLibraryPath(libraryPath);
    requireManagedLibrary(normalizedLibraryPath);
    Path checksumPath = checksumPath(normalizedLibraryPath);
    if (!Files.isRegularFile(checksumPath)) {
      throw missingChecksumFile(normalizedLibraryPath, checksumPath);
    }
    String expectedSha256 =
        expectedSha256(checksumPath, normalizedLibraryPath.getFileName().toString());
    String actualSha256 = actualSha256(normalizedLibraryPath);
    if (!actualSha256.equals(expectedSha256)) {
      throw mismatchedIdentity(
          normalizedLibraryPath,
          identitySourceDescription(checksumPath),
          expectedSha256,
          actualSha256);
    }
  }

  static void requireTrustedManagedLibrary(Path libraryPath) {
    requireTrustedManagedLibrary(libraryPath, SqliteManagedLibraryIdentity::trustedChecksumText);
  }

  static void requireTrustedManagedLibrary(Path libraryPath, TrustedChecksumTextReader reader) {
    Objects.requireNonNull(reader, "reader");
    Path normalizedLibraryPath = normalizedLibraryPath(libraryPath);
    requireManagedLibrary(normalizedLibraryPath);
    String trustedChecksumText;
    try {
      trustedChecksumText = reader.read(TRUSTED_IDENTITY_RESOURCE);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the trusted FinGrind managed SQLite digest resource "
              + TRUSTED_IDENTITY_RESOURCE
              + ".",
          exception);
    }
    if (trustedChecksumText == null || trustedChecksumText.isBlank()) {
      throw new IllegalStateException(
          "Trusted FinGrind managed SQLite digest resource "
              + TRUSTED_IDENTITY_RESOURCE
              + " is missing or empty.");
    }
    String expectedSha256 =
        expectedSha256(
            trustedChecksumText.lines().toList(),
            trustedIdentitySourceDescription(),
            normalizedLibraryPath.getFileName().toString());
    String actualSha256 = actualSha256(normalizedLibraryPath);
    if (!actualSha256.equals(expectedSha256)) {
      throw mismatchedIdentity(
          normalizedLibraryPath, trustedIdentitySourceDescription(), expectedSha256, actualSha256);
    }
  }

  static Path checksumPath(Path libraryPath) {
    Path normalizedLibraryPath = normalizedLibraryPath(libraryPath);
    return normalizedLibraryPath.resolveSibling(normalizedLibraryPath.getFileName() + ".sha256");
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

  private static void requireManagedLibrary(Path libraryPath) {
    if (!Files.isRegularFile(libraryPath)) {
      throw missingManagedLibrary(libraryPath);
    }
  }

  static @Nullable String trustedChecksumText(String resourcePath) throws IOException {
    return trustedChecksumText(
        SqliteManagedLibraryIdentity.class.getResourceAsStream(resourcePath));
  }

  static @Nullable String trustedChecksumText(@Nullable InputStream inputStream)
      throws IOException {
    if (inputStream == null) {
      return null;
    }
    try (InputStream trustedChecksumStream = inputStream) {
      return new String(trustedChecksumStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static UnsupportedManagedSqliteLibraryIdentityException mismatchedIdentity(
      Path libraryPath, String identitySource, String expectedSha256, String actualSha256) {
    return new UnsupportedManagedSqliteLibraryIdentityException(
        libraryPath, identitySource, expectedSha256, actualSha256);
  }

  private static String trustedIdentitySourceDescription() {
    return "trusted FinGrind managed SQLite digest resource " + TRUSTED_IDENTITY_RESOURCE;
  }

  private static String identitySourceDescription(Path checksumPath) {
    return "sibling SHA-256 file " + checksumPath.toAbsolutePath().normalize();
  }

  private static IllegalStateException missingManagedLibrary(Path libraryPath) {
    return new IllegalStateException(
        "Managed SQLite library does not exist at "
            + libraryPath
            + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.");
  }

  private static IllegalStateException missingChecksumFile(Path libraryPath, Path checksumPath) {
    return new IllegalStateException(
        "Managed SQLite library at "
            + libraryPath
            + " is missing the sibling SHA-256 file "
            + checksumPath
            + ". Rebuild the managed runtime with ./gradlew prepareManagedSqlite, or use the published FinGrind bundle without relocating library files.");
  }

  private static Path normalizedLibraryPath(Path libraryPath) {
    Objects.requireNonNull(libraryPath, "libraryPath");
    return libraryPath.toAbsolutePath().normalize();
  }

  private static Path createPrivateSnapshotDirectory() {
    Path tempRoot = normalizedLibraryPath(Path.of(System.getProperty("java.io.tmpdir")));
    return createPrivateSnapshotDirectory(tempRoot, supportsPosix(tempRoot));
  }

  /** Same-package seam for deterministic managed-snapshot temp-directory creation policy. */
  static Path createPrivateSnapshotDirectory(Path tempRoot, boolean supportsPosix) {
    Path normalizedTempRoot = normalizedLibraryPath(tempRoot);
    try {
      if (supportsPosix) {
        return Files.createTempDirectory(
            normalizedTempRoot,
            "fingrind-managed-sqlite-",
            PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY_PERMISSIONS));
      }
      return Files.createTempDirectory(normalizedTempRoot, "fingrind-managed-sqlite-");
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create a private managed SQLite verification snapshot directory.", exception);
    }
  }

  private static boolean supportsPosix(Path path) {
    try {
      return Files.getFileStore(path).supportsFileAttributeView("posix");
    } catch (IOException exception) {
      return false;
    }
  }

  /** Same-package seam for hardening one verified snapshot file with private permissions. */
  static void hardenPrivateFile(Path file) {
    if (!supportsPosix(file)) {
      return;
    }
    try {
      Files.setPosixFilePermissions(file, PRIVATE_FILE_PERMISSIONS);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to apply private managed SQLite snapshot permissions at " + file + ".",
          exception);
    }
  }

  private static void registerDeleteOnExit(Path path) {
    path.toFile().deleteOnExit();
  }

  private static String capitalize(String text) {
    if (text.isEmpty()) {
      return text;
    }
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  /** Same-package seam for reading the trusted managed-runtime digest resource. */
  @FunctionalInterface
  interface TrustedChecksumTextReader {
    /**
     * Reads the trusted managed-runtime digest resource and may return {@code null} when absent.
     */
    @Nullable String read(String resourcePath) throws IOException;
  }

  /** Private verified copy of one selected managed SQLite library plus its checksum sidecar. */
  record VerifiedLibrarySnapshot(
      SqliteLibraryTarget sourceTarget,
      Path snapshotDirectory,
      Path snapshotLibraryPath,
      Path snapshotChecksumPath) {
    VerifiedLibrarySnapshot {
      Objects.requireNonNull(sourceTarget, "sourceTarget");
      snapshotDirectory = normalizedLibraryPath(snapshotDirectory);
      snapshotLibraryPath = normalizedLibraryPath(snapshotLibraryPath);
      snapshotChecksumPath = normalizedLibraryPath(snapshotChecksumPath);
      if (!snapshotLibraryPath.startsWith(snapshotDirectory)) {
        throw new IllegalArgumentException(
            "snapshotLibraryPath must live inside snapshotDirectory.");
      }
      if (!snapshotChecksumPath.startsWith(snapshotDirectory)) {
        throw new IllegalArgumentException(
            "snapshotChecksumPath must live inside snapshotDirectory.");
      }
    }

    static VerifiedLibrarySnapshot copyOf(
        SqliteLibraryTarget sourceTarget, Path sourceLibraryPath, Path sourceChecksumPath) {
      Path snapshotDirectory = createPrivateSnapshotDirectory();
      Path snapshotLibraryPath = snapshotDirectory.resolve(sourceLibraryPath.getFileName());
      Path snapshotChecksumPath = snapshotDirectory.resolve(sourceChecksumPath.getFileName());
      registerDeleteOnExit(snapshotDirectory);
      registerDeleteOnExit(snapshotLibraryPath);
      registerDeleteOnExit(snapshotChecksumPath);
      try {
        Files.copy(sourceLibraryPath, snapshotLibraryPath, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sourceChecksumPath, snapshotChecksumPath, StandardCopyOption.REPLACE_EXISTING);
        hardenPrivateFile(snapshotLibraryPath);
        hardenPrivateFile(snapshotChecksumPath);
      } catch (IOException exception) {
        deleteQuietly(snapshotChecksumPath);
        deleteQuietly(snapshotLibraryPath);
        deleteQuietly(snapshotDirectory);
        throw new IllegalStateException(
            "Failed to create the private managed SQLite verification snapshot from "
                + sourceLibraryPath
                + ".",
            exception);
      }
      return new VerifiedLibrarySnapshot(
          sourceTarget, snapshotDirectory, snapshotLibraryPath, snapshotChecksumPath);
    }

    SqliteLibraryTarget runtimeTarget() {
      return new SqliteLibraryTarget(
          sourceTarget.mode(), sourceTarget.provenance(), snapshotLibraryPath.toString());
    }

    void deleteQuietly() {
      deleteQuietly(snapshotChecksumPath);
      deleteQuietly(snapshotLibraryPath);
      deleteQuietly(snapshotDirectory);
    }

    private static void deleteQuietly(Path path) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException ignored) {
        // Best-effort cleanup only; the snapshot is already scoped to the current user temp root.
      }
    }
  }
}
