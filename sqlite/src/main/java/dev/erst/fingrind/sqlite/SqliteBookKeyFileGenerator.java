package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.CryptographicPrimitives;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;
  private static final int GENERATED_RANDOM_BYTES = GENERATED_ENTROPY_BITS / 8;

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

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedBookKeyFile generate(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath).requireAccepted();
  }

  /** Creates one new key file and returns the explicit accepted/rejected result. */
  public static ContractDecision<GeneratedBookKeyFile> generateDecision(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath, SqliteBookKeyFileGenerator::writeAndVerifyFile);
  }

  static GeneratedBookKeyFile generate(
      Path bookKeyFilePath, GeneratedKeyFileMaterializer generatedKeyFileMaterializer) {
    return generateDecision(bookKeyFilePath, generatedKeyFileMaterializer).requireAccepted();
  }

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath, GeneratedKeyFileMaterializer generatedKeyFileMaterializer) {
    return generateDecision(
        bookKeyFilePath,
        generatedKeyFileMaterializer,
        SqliteBookKeyFileGenerator::ensureParentDirectory,
        SqliteBookKeyFileGenerator::createStageFile);
  }

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      GeneratedKeyFileMaterializer generatedKeyFileMaterializer,
      SecureParentDirectoryEnsurer secureParentDirectoryEnsurer,
      EmptyKeyFileCreator emptyKeyFileCreator) {
    Objects.requireNonNull(generatedKeyFileMaterializer, "generatedKeyFileMaterializer");
    Objects.requireNonNull(secureParentDirectoryEnsurer, "secureParentDirectoryEnsurer");
    Objects.requireNonNull(emptyKeyFileCreator, "emptyKeyFileCreator");
    Path normalizedPath = normalize(bookKeyFilePath);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    }
    byte[] encodedPassphrase = encodedPassphraseBytes();
    SqliteOwnedStagedArtifact stagedArtifact = null;
    boolean published = false;
    try {
      recoverOwnedStageIfParentExists(normalizedPath);
      SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
      secureParentDirectoryEnsurer.ensure(normalizedPath);
      SqliteGeneratedSecretTarget secretTarget =
          SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
      SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(normalizedPath);
      stagedArtifact = secretTarget.createStage(".generated-key-", ".tmp");
      Path stagedPath = stagedArtifact.stagedPath();
      Files.deleteIfExists(stagedPath);
      ContractDecision<Path> createdFile = emptyKeyFileCreator.create(stagedPath);
      switch (createdFile) {
        case ContractDecision.Accepted<Path> _ -> {}
        case ContractDecision.Rejected<Path>(var failure) -> {
          return ContractDecision.rejected(failure);
        }
      }
      generatedKeyFileMaterializer.materialize(stagedPath, encodedPassphrase);
      secretTarget.publishRetainingStage(stagedPath);
      published = true;
      stagedArtifact.discard();
      return ContractDecision.accepted(
          new GeneratedBookKeyFile(
              normalizedPath,
              GENERATED_ENCODING,
              GENERATED_ENTROPY_BITS,
              SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath)));
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(exception.targetPath()));
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create the FinGrind book key file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    } finally {
      if (!published && stagedArtifact != null) {
        stagedArtifact.discard();
      }
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

  private static void recoverOwnedStageIfParentExists(Path normalizedPath) {
    Path parent = normalizedPath.getParent();
    if (parent != null && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      SqliteOwnedStagedArtifact.recoverFor(normalizedPath);
    }
  }

  static void ensureParentDirectory(Path normalizedPath) throws IOException {
    SqliteBookKeyFileSecurity.ensureSecureParentDirectory(normalizedPath);
  }

  private static ContractDecision<Path> createStageFile(Path normalizedPath) throws IOException {
    SqliteBookKeyFileSecurity.createSecureEmptyFile(normalizedPath);
    return ContractDecision.accepted(normalizedPath);
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure secretTargetOccupiedFailure(
      Path targetPath) {
    return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
        targetPath,
        "Generated secret target already exists and will not be overwritten.",
        "Choose an absent --new-book-key-file path or remove the existing file yourself before retrying.",
        "--new-book-key-file");
  }

  private static void writeFile(Path normalizedPath, byte[] encodedPassphrase) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            normalizedPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      channel.write(ByteBuffer.wrap(encodedPassphrase));
      channel.force(true);
    }
  }

  private static byte[] encodedPassphraseBytes() {
    byte[] randomBytes = CryptographicPrimitives.secureBytes(GENERATED_RANDOM_BYTES);
    try {
      return Base64.getUrlEncoder().withoutPadding().encode(randomBytes);
    } finally {
      Arrays.fill(randomBytes, (byte) 0);
    }
  }
}
