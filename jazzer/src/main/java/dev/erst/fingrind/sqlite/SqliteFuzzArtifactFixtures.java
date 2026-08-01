package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PrivateOutputFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Owner-only artifact fixtures shared by Jazzer SQLite workflows. */
public final class SqliteFuzzArtifactFixtures {
  private static final String TEST_BOOK_KEY = "fingrind-jazzer-book-key";
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  private SqliteFuzzArtifactFixtures() {}

  /** Writes one deterministic secure key file beneath an already-admitted direct parent. */
  public static void writeDeterministicBookKeyFile(Path keyFilePath) throws java.io.IOException {
    Path normalizedKeyFilePath = keyFilePath.toAbsolutePath().normalize();
    Path parentDirectory =
        Objects.requireNonNull(normalizedKeyFilePath.getParent(), "normalizedKeyFilePath parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    if (Files.notExists(normalizedKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      SqliteBookKeyFileGenerator.generate(normalizedKeyFilePath);
    } else {
      SqliteBookKeyFile.loadDecision(normalizedKeyFilePath).requireAccepted().close();
    }
    replaceFixtureKeyFile(normalizedKeyFilePath);
  }

  /** Atomically writes one bounded passphrase fixture to a new owner-only file. */
  public static void writeNewOwnerOnlyFixturePassphraseFile(
      Path passphraseFilePath, String passphrase) throws java.io.IOException {
    Path normalizedPassphraseFilePath =
        Objects.requireNonNull(passphraseFilePath, "passphraseFilePath")
            .toAbsolutePath()
            .normalize();
    Path parentDirectory =
        Objects.requireNonNull(
            normalizedPassphraseFilePath.getParent(), "normalizedPassphraseFilePath parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    byte[] content =
        Objects.requireNonNull(passphrase, "passphrase").getBytes(StandardCharsets.UTF_8);
    try {
      if (content.length > ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES) {
        throw new java.io.IOException(
            "Jazzer fixture passphrase exceeds the supported UTF-8 byte limit.");
      }
      try (PrivateOutputFile.OpenedFile channel =
          SqliteOwnedRegularFileAccess.openNewWrite(normalizedPassphraseFilePath)) {
        writeFullyAndForce(channel, ByteBuffer.wrap(content), "Jazzer fixture passphrase");
      }
    } finally {
      Arrays.fill(content, (byte) 0);
    }
  }

  /** Creates one absent POSIX owner-only artifact directory, then validates the result. */
  public static Path createOwnerOnlyArtifactDirectory(Path directoryPath)
      throws java.io.IOException {
    Path normalizedDirectory = normalizedArtifactDirectory(directoryPath);
    Path parentDirectory =
        Objects.requireNonNull(normalizedDirectory.getParent(), "normalizedDirectory parent");
    requireOwnerOnlyArtifactDirectory(parentDirectory);
    requirePosixFileAttributeView(
        normalizedDirectory,
        Files.getFileAttributeView(
            parentDirectory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS),
        "SQLite fuzz artifact directory creation");
    Files.createDirectory(
        normalizedDirectory,
        PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS));
    return requireOwnerOnlyArtifactDirectory(normalizedDirectory);
  }

  /** Creates one fresh owner-only temporary artifact directory, then validates the result. */
  public static Path createOwnerOnlyTemporaryArtifactDirectory(String prefix)
      throws java.io.IOException {
    Path temporaryRoot =
        Path.of(Objects.requireNonNull(System.getProperty("java.io.tmpdir"), "java.io.tmpdir"))
            .toAbsolutePath()
            .normalize();
    Path canonicalTemporaryRoot = requireTemporaryArtifactRoot(temporaryRoot);
    requirePosixFileAttributeView(
        canonicalTemporaryRoot,
        Files.getFileAttributeView(
            canonicalTemporaryRoot, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS),
        "Jazzer temporary artifact creation");
    Path directory =
        Files.createTempDirectory(
            canonicalTemporaryRoot,
            Objects.requireNonNull(prefix, "prefix"),
            PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS));
    return requireOwnerOnlyArtifactDirectory(directory);
  }

  /** Validates one existing owner-only artifact directory without changing it. */
  public static Path requireOwnerOnlyArtifactDirectory(Path directoryPath)
      throws java.io.IOException {
    Path normalizedDirectory = normalizedArtifactDirectory(directoryPath);
    PrivateOutputDirectory.requireExistingOwnerOnly(normalizedDirectory);
    return normalizedDirectory;
  }

  static Path requireTemporaryArtifactRoot(Path temporaryRoot) throws java.io.IOException {
    if (!Files.isDirectory(temporaryRoot, LinkOption.NOFOLLOW_LINKS)) {
      throw new java.io.IOException(
          "Jazzer temporary artifact root must be an existing real directory: " + temporaryRoot);
    }
    Path canonicalTemporaryRoot = temporaryRoot.toRealPath();
    PrivateOutputDirectory.requireCreationAncestry(canonicalTemporaryRoot);
    return canonicalTemporaryRoot;
  }

  static void requirePosixFileAttributeView(
      Path artifactPath, PosixFileAttributeView attributeView, String operation)
      throws java.io.IOException {
    if (attributeView == null) {
      throw new java.io.IOException(
          Objects.requireNonNull(operation, "operation")
              + " requires POSIX owner-only permissions: "
              + Objects.requireNonNull(artifactPath, "artifactPath"));
    }
  }

  private static Path normalizedArtifactDirectory(Path directoryPath) {
    return Objects.requireNonNull(directoryPath, "directoryPath").toAbsolutePath().normalize();
  }

  private static void replaceFixtureKeyFile(Path keyFilePath) throws java.io.IOException {
    ByteBuffer content = StandardCharsets.UTF_8.encode(TEST_BOOK_KEY);
    try (PrivateOutputFile.OpenedFile channel =
        SqliteOwnedRegularFileAccess.openTruncatingWrite(keyFilePath)) {
      writeFullyAndForce(channel, content, "deterministic SQLite fuzz key material");
    }
  }

  static void writeFullyAndForce(
      PrivateOutputFile.OpenedFile channel, ByteBuffer content, String artifactDescription)
      throws java.io.IOException {
    while (content.hasRemaining()) {
      if (channel.write(content) <= 0) {
        throw new java.io.IOException(
            "Could not make progress writing " + Objects.requireNonNull(artifactDescription) + ".");
      }
    }
    channel.force();
  }
}
