package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

/** Writes a resolved book passphrase into one new owner-only FinGrind key file. */
final class SqliteBookKeyFileMaterializer {
  private SqliteBookKeyFileMaterializer() {}

  static Path materialize(Path bookKeyFilePath, SqliteBookPassphrase bookPassphrase) {
    Path normalizedPath = normalize(bookKeyFilePath);
    SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    requireMissingDestination(normalizedPath);
    byte[] encodedPassphrase =
        Objects.requireNonNull(bookPassphrase, "bookPassphrase").utf8BytesCopy();
    boolean created = false;
    try {
      SqliteBookKeyFileSecurity.ensureSecureParentDirectory(normalizedPath);
      SqliteBookKeyFileSecurity.createSecureEmptyFile(normalizedPath);
      created = true;
      writeAndVerifyFile(normalizedPath, encodedPassphrase);
      return normalizedPath;
    } catch (IOException exception) {
      if (created) {
        SqliteBookKeyFileGenerator.deleteQuietly(normalizedPath);
      }
      throw new IllegalStateException(
          "Failed to create the FinGrind backup key file: "
              + PublicPathHint.fromPath(normalizedPath).value(),
          exception);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }

  private static void requireMissingDestination(Path normalizedPath) {
    if (Files.exists(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException(
          "The FinGrind backup key file destination already exists and will not be overwritten: "
              + PublicPathHint.fromPath(normalizedPath).value());
    }
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static void writeAndVerifyFile(Path normalizedPath, byte[] encodedPassphrase)
      throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            normalizedPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      channel.write(ByteBuffer.wrap(encodedPassphrase));
      channel.force(true);
    }
    SqliteBookKeyFile.requireSecureKeyFile(normalizedPath).requireAccepted();
  }
}
