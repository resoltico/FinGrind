package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** Reads one UTF-8 book passphrase file. */
public final class SqliteBookKeyFile {
  private static final String KEY_FILE_SOURCE_LABEL = "key file";

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
                                KEY_FILE_SOURCE_LABEL, loadedBytes),
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
          try (InputStream inputStream = SqliteSecureRegularFileAccess.openRead(path)) {
            return readBoundedPassphraseBytes(inputStream);
          }
        });
  }

  static ContractDecision<byte[]> readBytes(Path bookKeyFilePath, ByteLoader byteLoader) {
    Objects.requireNonNull(byteLoader, "byteLoader");
    try {
      return ContractDecision.accepted(byteLoader.load(bookKeyFilePath));
    } catch (SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failureAt(
              bookKeyFilePath,
              "FinGrind book passphrase input from the selected key file exceeded the %d-byte limit."
                  .formatted(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES),
              "Provide one key file whose single-line UTF-8 passphrase fits within the %d-byte limit, then rerun the command."
                  .formatted(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES),
              null));
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
              bookKeyFilePath,
              "Failed to read the FinGrind book key file.",
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
    return SqliteBookPassphraseSourceBytes.read(inputStream);
  }
}
