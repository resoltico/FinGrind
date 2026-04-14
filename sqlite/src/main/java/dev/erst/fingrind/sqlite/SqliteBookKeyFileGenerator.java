package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;
  static final String GENERATED_FILE_MODE = "0600";

  private static final Set<PosixFilePermission> KEY_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> KEY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final int GENERATED_RANDOM_BYTES = GENERATED_ENTROPY_BITS / 8;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Internal seam for materializing a newly created key file during generator tests. */
  @FunctionalInterface
  interface GeneratedKeyFileMaterializer {
    /** Writes and verifies one newly created key file at the normalized destination path. */
    void materialize(Path normalizedPath, byte[] encodedPassphrase) throws IOException;
  }

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedKeyFile generate(Path bookKeyFilePath) {
    return generate(bookKeyFilePath, SECURE_RANDOM, SqliteBookKeyFileGenerator::writeAndVerifyFile);
  }

  static GeneratedKeyFile generate(Path bookKeyFilePath, SecureRandom secureRandom) {
    return generate(bookKeyFilePath, secureRandom, SqliteBookKeyFileGenerator::writeAndVerifyFile);
  }

  static GeneratedKeyFile generate(
      Path bookKeyFilePath,
      SecureRandom secureRandom,
      GeneratedKeyFileMaterializer generatedKeyFileMaterializer) {
    Objects.requireNonNull(secureRandom, "secureRandom");
    Objects.requireNonNull(generatedKeyFileMaterializer, "generatedKeyFileMaterializer");
    Path normalizedPath = normalize(bookKeyFilePath);
    requirePosixFileSystem(normalizedPath);
    byte[] encodedPassphrase = encodedPassphraseBytes(secureRandom);
    boolean created = false;
    try {
      ensureParentDirectory(normalizedPath);
      createFile(normalizedPath);
      created = true;
      generatedKeyFileMaterializer.materialize(normalizedPath, encodedPassphrase);
      return new GeneratedKeyFile(
          normalizedPath, GENERATED_ENCODING, GENERATED_ENTROPY_BITS, GENERATED_FILE_MODE);
    } catch (IOException exception) {
      if (created) {
        deleteQuietly(normalizedPath);
      }
      throw new IllegalStateException(
          "Failed to create the FinGrind book key file: " + normalizedPath, exception);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }

  private static void writeAndVerifyFile(Path normalizedPath, byte[] encodedPassphrase)
      throws IOException {
    writeFile(normalizedPath, encodedPassphrase);
    SqliteBookKeyFile.requireSecureKeyFile(normalizedPath);
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static void requirePosixFileSystem(Path normalizedPath) {
    if (!normalizedPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      throw new IllegalStateException(
          "The FinGrind book key file must live on a POSIX filesystem so owner-only permissions can be enforced: "
              + normalizedPath);
    }
  }

  private static void ensureParentDirectory(Path normalizedPath) throws IOException {
    Path parentDirectory = normalizedPath.getParent();
    if (parentDirectory == null) {
      return;
    }
    Files.createDirectories(
        parentDirectory, PosixFilePermissions.asFileAttribute(KEY_DIRECTORY_PERMISSIONS));
  }

  private static void createFile(Path normalizedPath) throws IOException {
    try {
      Files.createFile(normalizedPath, PosixFilePermissions.asFileAttribute(KEY_FILE_PERMISSIONS));
    } catch (FileAlreadyExistsException exception) {
      throw new IllegalStateException(
          "The FinGrind book key file already exists and will not be overwritten: "
              + normalizedPath,
          exception);
    }
  }

  private static void writeFile(Path normalizedPath, byte[] encodedPassphrase) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            normalizedPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      channel.write(ByteBuffer.wrap(encodedPassphrase));
      channel.force(true);
    }
  }

  private static byte[] encodedPassphraseBytes(SecureRandom secureRandom) {
    byte[] randomBytes = new byte[GENERATED_RANDOM_BYTES];
    try {
      secureRandom.nextBytes(randomBytes);
      return Base64.getUrlEncoder().withoutPadding().encode(randomBytes);
    } finally {
      Arrays.fill(randomBytes, (byte) 0);
    }
  }

  private static void deleteQuietly(Path normalizedPath) {
    try {
      Files.deleteIfExists(normalizedPath);
    } catch (IOException ignored) {
      // Preserve the original failure.
    }
  }

  /** Non-secret metadata describing one newly created key file. */
  public record GeneratedKeyFile(
      Path bookKeyFilePath, String encoding, int entropyBits, String permissions) {
    public GeneratedKeyFile {
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      if (Files.exists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)
          && !Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException("bookKeyFilePath must identify a regular file.");
      }
      Objects.requireNonNull(encoding, "encoding");
      if (encoding.isBlank()) {
        throw new IllegalArgumentException("encoding must not be blank.");
      }
      if (entropyBits <= 0) {
        throw new IllegalArgumentException("entropyBits must be positive.");
      }
      Objects.requireNonNull(permissions, "permissions");
      if (permissions.isBlank()) {
        throw new IllegalArgumentException("permissions must not be blank.");
      }
    }
  }
}
