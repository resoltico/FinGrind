package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.io.IOException;
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
    try {
      return ContractDecision.accepted(Files.readAllBytes(bookKeyFilePath));
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
}
