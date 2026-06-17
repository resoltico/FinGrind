package dev.erst.fingrind.sqlite;

import static java.lang.System.Logger.Level.WARNING;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
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
  private static final System.Logger LOGGER =
      System.getLogger(SqliteBookKeyFileGenerator.class.getName());

  /** Internal seam for materializing a newly created key file during generator tests. */
  @FunctionalInterface
  interface GeneratedKeyFileMaterializer {
    /** Writes and verifies one newly created key file at the normalized destination path. */
    void materialize(Path normalizedPath, byte[] encodedPassphrase) throws IOException;
  }

  /** Internal seam for securing the destination parent directory during generator tests. */
  @FunctionalInterface
  interface SecureParentDirectoryEnsurer {
    /** Ensures the normalized destination path resolves under one secure parent directory. */
    void ensure(Path normalizedPath) throws IOException;
  }

  /** Internal seam for reserving one empty key-file path during generator tests. */
  @FunctionalInterface
  interface EmptyKeyFileCreator {
    /** Creates one empty key file or returns the shaped rejection for an occupied destination. */
    ContractDecision<Path> create(Path normalizedPath) throws IOException;
  }

  /** Internal seam for reporting best-effort cleanup failures during generator tests. */
  @FunctionalInterface
  interface DeleteFailureReporter {
    /** Handles one non-fatal cleanup failure while preserving the primary outcome. */
    void report(String action, Exception exception);
  }

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedBookKeyFile generate(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath).requireAccepted();
  }

  /** Creates one new key file and returns the explicit accepted/rejected result. */
  public static ContractDecision<GeneratedBookKeyFile> generateDecision(Path bookKeyFilePath) {
    return generateDecision(
        bookKeyFilePath, SECURE_RANDOM, SqliteBookKeyFileGenerator::writeAndVerifyFile);
  }

  static GeneratedBookKeyFile generate(Path bookKeyFilePath, SecureRandom secureRandom) {
    return generateDecision(bookKeyFilePath, secureRandom).requireAccepted();
  }

  static GeneratedBookKeyFile generate(
      Path bookKeyFilePath,
      SecureRandom secureRandom,
      GeneratedKeyFileMaterializer generatedKeyFileMaterializer) {
    return generateDecision(bookKeyFilePath, secureRandom, generatedKeyFileMaterializer)
        .requireAccepted();
  }

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath, SecureRandom secureRandom) {
    return generateDecision(
        bookKeyFilePath, secureRandom, SqliteBookKeyFileGenerator::writeAndVerifyFile);
  }

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SecureRandom secureRandom,
      GeneratedKeyFileMaterializer generatedKeyFileMaterializer) {
    return generateDecision(
        bookKeyFilePath,
        secureRandom,
        generatedKeyFileMaterializer,
        SqliteBookKeyFileGenerator::ensureParentDirectory,
        SqliteBookKeyFileGenerator::createFile);
  }

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SecureRandom secureRandom,
      GeneratedKeyFileMaterializer generatedKeyFileMaterializer,
      SecureParentDirectoryEnsurer secureParentDirectoryEnsurer,
      EmptyKeyFileCreator emptyKeyFileCreator) {
    Objects.requireNonNull(secureRandom, "secureRandom");
    Objects.requireNonNull(generatedKeyFileMaterializer, "generatedKeyFileMaterializer");
    Objects.requireNonNull(secureParentDirectoryEnsurer, "secureParentDirectoryEnsurer");
    Objects.requireNonNull(emptyKeyFileCreator, "emptyKeyFileCreator");
    Path normalizedPath = normalize(bookKeyFilePath);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    }
    byte[] encodedPassphrase = encodedPassphraseBytes(secureRandom);
    boolean created = false;
    try {
      secureParentDirectoryEnsurer.ensure(normalizedPath);
      ContractDecision<Path> createdFile = emptyKeyFileCreator.create(normalizedPath);
      switch (createdFile) {
        case ContractDecision.Accepted<Path> _ -> {}
        case ContractDecision.Rejected<Path>(var failure) -> {
          return ContractDecision.rejected(failure);
        }
      }
      created = true;
      generatedKeyFileMaterializer.materialize(normalizedPath, encodedPassphrase);
      return ContractDecision.accepted(
          new GeneratedBookKeyFile(
              normalizedPath,
              GENERATED_ENCODING,
              GENERATED_ENTROPY_BITS,
              SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath)));
    } catch (SqliteCallerPathContractException exception) {
      if (created) {
        deleteQuietly(normalizedPath);
      }
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      if (created) {
        deleteQuietly(normalizedPath);
      }
      throw new IllegalStateException(
          "Failed to create the FinGrind book key file: "
              + PublicPathHint.fromPath(normalizedPath).value(),
          exception);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }

  private static void writeAndVerifyFile(Path normalizedPath, byte[] encodedPassphrase)
      throws IOException {
    writeFile(normalizedPath, encodedPassphrase);
    SqliteBookKeyFile.requireSecureKeyFile(normalizedPath).requireAccepted();
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  static void ensureParentDirectory(Path normalizedPath) throws IOException {
    SqliteBookKeyFileSecurity.ensureSecureParentDirectory(normalizedPath);
  }

  private static ContractDecision<Path> createFile(Path normalizedPath) throws IOException {
    try {
      SqliteBookKeyFileSecurity.createSecureEmptyFile(normalizedPath);
      return ContractDecision.accepted(normalizedPath);
    } catch (FileAlreadyExistsException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.BOOK_KEY_FILE_ALREADY_EXISTS.failure(
              "The FinGrind book key file already exists and will not be overwritten: "
                  + PublicPathHint.fromPath(normalizedPath).value(),
              "Choose a different destination path for "
                  + ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE)
                  + ", or remove the existing file yourself before rerunning.",
              null));
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

  /**
   * Deletes one normalized key-file path as best-effort cleanup after generation failure.
   *
   * <p>This helper preserves the primary generation failure by logging cleanup problems instead of
   * surfacing them as the main outcome.
   */
  public static void deleteQuietly(Path normalizedPath) {
    deleteQuietly(normalizedPath, SqliteBookKeyFileGenerator::reportCleanupFailure);
  }

  static void deleteQuietly(Path normalizedPath, DeleteFailureReporter reporter) {
    Objects.requireNonNull(reporter, "reporter");
    try {
      Files.deleteIfExists(normalizedPath);
    } catch (IOException exception) {
      reporter.report("deleting one partially created book-key path", exception);
    }
  }

  private static void reportCleanupFailure(String action, Exception exception) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(exception, "exception");
    LOGGER.log(
        WARNING,
        "SQLite best-effort cleanup failed during " + action + "; preserving the primary outcome.",
        exception);
  }
}
