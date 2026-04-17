package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;
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
    SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    byte[] encodedPassphrase = encodedPassphraseBytes(secureRandom);
    boolean created = false;
    try {
      ensureParentDirectory(normalizedPath);
      createFile(normalizedPath);
      created = true;
      generatedKeyFileMaterializer.materialize(normalizedPath, encodedPassphrase);
      return new GeneratedKeyFile(
          normalizedPath,
          GENERATED_ENCODING,
          GENERATED_ENTROPY_BITS,
          SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath));
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

  private static void ensureParentDirectory(Path normalizedPath) throws IOException {
    SqliteBookKeyFileSecurity.ensureSecureParentDirectory(normalizedPath);
  }

  private static void createFile(Path normalizedPath) throws IOException {
    try {
      SqliteBookKeyFileSecurity.createSecureEmptyFile(normalizedPath);
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
