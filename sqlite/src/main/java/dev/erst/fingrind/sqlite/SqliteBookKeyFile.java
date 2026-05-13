package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads one UTF-8 book passphrase file. */
public final class SqliteBookKeyFile {
  private SqliteBookKeyFile() {}

  /** Reads and normalizes one UTF-8 passphrase file. */
  public static SqliteBookPassphrase load(Path bookKeyFilePath) {
    return loadDecision(bookKeyFilePath).requireAccepted();
  }

  /** Reads and normalizes one UTF-8 passphrase file with an explicit accepted/rejected result. */
  public static ContractDecision<SqliteBookPassphrase> loadDecision(Path bookKeyFilePath) {
    Path normalizedPath = normalize(bookKeyFilePath);
    return requireSecureKeyFile(normalizedPath)
        .fold(
            validatedPath ->
                readBytes(validatedPath)
                    .fold(
                        loadedBytes ->
                            SqliteBookPassphrase.fromUtf8BytesDecision(
                                validatedPath.toString(), loadedBytes),
                        ContractDecision::rejected),
            ContractDecision::rejected);
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static ContractDecision<byte[]> readBytes(Path bookKeyFilePath) {
    return readBytes(
        bookKeyFilePath,
        path -> {
          try (InputStream inputStream = Files.newInputStream(path)) {
            return readBoundedPassphraseBytes(inputStream);
          }
        });
  }

  static ContractDecision<byte[]> readBytes(Path bookKeyFilePath, ByteLoader byteLoader) {
    Objects.requireNonNull(byteLoader, "byteLoader");
    try {
      return ContractDecision.accepted(byteLoader.load(bookKeyFilePath));
    } catch (OversizedBookPassphraseSourceException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
              "FinGrind book passphrase input from the selected key file exceeded the %d-byte limit: %s"
                  .formatted(
                      SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES,
                      bookKeyFilePath.toAbsolutePath().normalize()),
              "Provide one key file whose single-line UTF-8 passphrase fits within the %d-byte limit, then rerun the command."
                  .formatted(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES),
              null));
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
              "Failed to read the FinGrind book key file: " + bookKeyFilePath,
              "Inspect the selected book key file path, permissions, and filesystem accessibility, then rerun the command.",
              null));
    }
  }

  static ContractDecision<Path> requireSecureKeyFile(Path bookKeyFilePath) {
    return SqliteBookKeyFileSecurity.requireSecureKeyFile(bookKeyFilePath);
  }

  /** Loads raw bytes from one validated book-key file path for the same-package loader seam. */
  @FunctionalInterface
  interface ByteLoader {
    /** Reads the complete book-key payload from the supplied validated path. */
    byte[] load(Path bookKeyFilePath) throws IOException;
  }

  private static byte[] readBoundedPassphraseBytes(InputStream inputStream) throws IOException {
    byte[] buffer = inputStream.readNBytes(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1);
    if (buffer.length <= SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES) {
      return buffer;
    }
    throw new OversizedBookPassphraseSourceException();
  }

  /** Signals that one passphrase-source payload exceeded FinGrind's byte limit. */
  private static final class OversizedBookPassphraseSourceException extends IOException {
    private static final long serialVersionUID = 1L;
  }
}
