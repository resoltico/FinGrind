package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationStages;
import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;
  private static final int GENERATED_RANDOM_BYTES = GENERATED_ENTROPY_BITS / 8;
  private static final String STAGE_PREFIX = ".fingrind-generated-book-key-";
  private static final String STAGE_SUFFIX = ".tmp";
  private static final String NEW_BOOK_KEY_FILE_ARGUMENT = "--new-book-key-file";

  /** Injectable directory-force boundary for the one post-link durability proof. */
  @FunctionalInterface
  interface ParentDirectoryForcer {
    /** Force-confirms the final-name mutation in the exact selected parent directory. */
    void force(Path parentDirectory) throws IOException;
  }

  /** Injectable private-stage creation boundary for retained-stage failure evidence. */
  @FunctionalInterface
  interface RetainedStageCreator {
    /** Creates and returns the exact retained stage for one normalized final key-file path. */
    ArtifactPublicationRetention create(Path normalizedBookKeyFilePath, byte[] encodedPassphrase);
  }

  /** Injectable private-stage write boundary for materialization-failure classification. */
  @FunctionalInterface
  interface PrivateStageWriter {
    /** Materializes one fresh private stage beneath the selected owner-only parent directory. */
    Path createAndWrite(Path parentDirectory, String prefix, String suffix, byte[] bytes)
        throws IOException;
  }

  /** Injectable retained-witness acquisition boundary for publication-capability fault tests. */
  @FunctionalInterface
  interface PublicationCapabilityWitnessAcquirer {
    /** Acquires the exact no-replace publication witness for one normalized key-file target. */
    SqlitePublicationCapabilityWitness.Set acquire(Path normalizedBookKeyFilePath)
        throws IOException;
  }

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedBookKeyFile generate(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath).requireAccepted();
  }

  /** Creates one new key file and returns the explicit accepted/rejected result. */
  public static ContractDecision<GeneratedBookKeyFile> generateDecision(Path bookKeyFilePath) {
    return generateDecision(
        bookKeyFilePath, Files::createLink, AttestationDirectoryDurability::force);
  }

  /**
   * Creates one key file through explicit final-link and directory-durability boundaries.
   *
   * <p>This package-visible overload exists so fault tests can prove that an uncertain final link
   * or an unconfirmed directory force retains and reports the exact stage rather than deleting it.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer) {
    return generateDecision(
        bookKeyFilePath,
        finalLinkCreator,
        parentDirectoryForcer,
        SqliteBookKeyFileGenerator::createRetainedStage,
        SqliteBookKeyFileGenerator::acquirePublicationCapabilityWitness);
  }

  /**
   * Creates one key file through explicit final-link, directory-durability, and stage boundaries.
   *
   * <p>The package-visible stage creator keeps retained-stage failure semantics independently
   * executable: a failed publication must report the exact already-materialized private stage.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer,
      RetainedStageCreator retainedStageCreator) {
    return generateDecision(
        bookKeyFilePath,
        finalLinkCreator,
        parentDirectoryForcer,
        retainedStageCreator,
        SqliteBookKeyFileGenerator::acquirePublicationCapabilityWitness);
  }

  /**
   * Creates one key file with an explicit retained-witness acquisition boundary.
   *
   * <p>The package-visible acquisition seam proves that storage capability failures are classified
   * before secret material is staged, and that only recognized primitive refusals become caller
   * path rejections.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer,
      RetainedStageCreator retainedStageCreator,
      PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer) {
    Objects.requireNonNull(finalLinkCreator, "finalLinkCreator");
    Objects.requireNonNull(parentDirectoryForcer, "parentDirectoryForcer");
    Objects.requireNonNull(retainedStageCreator, "retainedStageCreator");
    Objects.requireNonNull(capabilityWitnessAcquirer, "capabilityWitnessAcquirer");
    Path normalizedPath = normalize(bookKeyFilePath);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
      requireAtomicPrivateStageCreation(normalizedPath);
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    }
    byte[] encodedPassphrase = encodedPassphraseBytes();
    try {
      // Check occupancy before parent validation so an existing root path still reports its actual
      // no-overwrite refusal rather than a synthetic parent-path error.
      SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
      SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(normalizedPath);
      SqliteGeneratedSecretTarget secretTarget =
          SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses;
      try {
        capabilityWitnesses = capabilityWitnessAcquirer.acquire(normalizedPath);
      } catch (SqlitePublicationCapabilityWitness.AcquisitionFailure exception) {
        throw exception;
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to establish the retained FinGrind generated-secret publication witness: "
                + SqliteMachinePaths.absoluteValue(normalizedPath),
            exception);
      }
      try (capabilityWitnesses) {
        ArtifactPublicationRetention retention =
            retainedStageCreator.create(normalizedPath, encodedPassphrase);
        ContractDecision<Path> secureStage =
            SqliteBookKeyFile.requireSecureKeyFile(retention.retainedStagePath());
        switch (secureStage) {
          case ContractDecision.Accepted<Path> _ -> {}
          case ContractDecision.Rejected<Path>(ContractFailure failure) -> {
            return ContractDecision.rejected(
                ContractErrors.withRetainedArtifactStage(failure, retention));
          }
        }
        ArtifactPublicationResult publication = publicationFact(normalizedPath, retention);
        ContractDecision<ArtifactPublicationRetention> linkDecision =
            linkFinalKeyFile(
                secretTarget,
                normalizedPath,
                publication.retention(),
                capabilityWitnesses,
                finalLinkCreator);
        switch (linkDecision) {
          case ContractDecision.Accepted<ArtifactPublicationRetention> _ -> {}
          case ContractDecision.Rejected<ArtifactPublicationRetention>(ContractFailure failure) -> {
            return ContractDecision.rejected(failure);
          }
        }
        try {
          parentDirectoryForcer.force(requiredParent(publication.publishedArtifactPath()));
        } catch (IOException | RuntimeException exception) {
          return ContractDecision.rejected(
              ContractErrors.artifactPublicationDurabilityUncertainFailure(
                  publication, NEW_BOOK_KEY_FILE_ARGUMENT));
        }
        return ContractDecision.accepted(
            new GeneratedBookKeyFile(
                publication,
                GENERATED_ENCODING,
                GENERATED_ENTROPY_BITS,
                SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath)));
      }
    } catch (SqliteBookKeyFileRetainedStageMaterializationFailure exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
                  normalizedPath,
                  "FinGrind could not materialize a private owner-only stage for the requested book key file.",
                  "Preserve the reported retained stage, inspect the selected key-file parent directory and filesystem, then choose a fresh --new-book-key-file destination before retrying.",
                  NEW_BOOK_KEY_FILE_ARGUMENT),
              exception.retention()));
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(exception.targetPath()));
    } catch (SqlitePublicationCapabilityWitness.AcquisitionFailure exception) {
      @org.jspecify.annotations.Nullable SqliteCallerPathContractException pathFailure =
          SqlitePublicationCapabilityWitness.callerPathFailure(
              exception, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED);
      if (pathFailure != null) {
        return ContractDecision.rejected(
            SqliteCallerPathFailureMapper.invalidBookKeyFile(pathFailure));
      }
      throw new IllegalStateException(
          "Failed to establish the retained FinGrind generated-secret publication witness: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    } catch (ContractFailureException exception) {
      return ContractDecision.rejected(exception.failure());
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to validate the private parent directory for the FinGrind book key file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }

  private static SqlitePublicationCapabilityWitness.Set acquirePublicationCapabilityWitness(
      Path normalizedPath) throws IOException {
    return SqlitePublicationCapabilityWitness.acquire(
        List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(normalizedPath)),
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  private static ArtifactPublicationRetention createRetainedStage(
      Path normalizedPath, byte[] encodedPassphrase) {
    return createRetainedStage(
        normalizedPath, encodedPassphrase, ArtifactPublicationStages::createAndWrite);
  }

  /** Materializes one retained private stage while preserving its exact failure evidence. */
  static ArtifactPublicationRetention createRetainedStage(
      Path normalizedPath, byte[] encodedPassphrase, PrivateStageWriter privateStageWriter) {
    try {
      return new ArtifactPublicationRetention(
          privateStageWriter.createAndWrite(
              requiredParent(normalizedPath), STAGE_PREFIX, STAGE_SUFFIX, encodedPassphrase));
    } catch (ArtifactPublicationRetainedStageException exception) {
      throw new SqliteBookKeyFileRetainedStageMaterializationFailure(
          exception.retainedStage(), exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create the FinGrind book-key private stage: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    }
  }

  private static ArtifactPublicationResult publicationFact(
      Path normalizedPath, ArtifactPublicationRetention retention) {
    try {
      return new ArtifactPublicationResult(normalizedPath, retention);
    } catch (IllegalArgumentException exception) {
      throw new SqliteBookKeyFileRetainedStageMaterializationFailure(retention, exception);
    }
  }

  private static ContractDecision<ArtifactPublicationRetention> linkFinalKeyFile(
      SqliteGeneratedSecretTarget secretTarget,
      Path normalizedPath,
      ArtifactPublicationRetention retention,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator) {
    try {
      secretTarget.publishRetainingStage(
          retention.retainedStagePath(),
          (finalPath, candidateStagePath) -> {
            createWitnessedFinalKeyLink(
                capabilityWitnesses, finalLinkCreator, finalPath, candidateStagePath);
          });
      return ContractDecision.accepted(retention);
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              secretTargetOccupiedFailure(exception.targetPath()), retention));
    } catch (SqliteBookKeyFileFinalLinkAdmissionFailure exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
                  normalizedPath,
                  "FinGrind could not confirm that the selected book-key target remained eligible for atomic no-replace publication.",
                  "Preserve the reported retained stage, inspect the selected target and its private parent directory, then choose a fresh --new-book-key-file destination before retrying.",
                  NEW_BOOK_KEY_FILE_ARGUMENT),
              retention));
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              SqliteCallerPathFailureMapper.invalidBookKeyFile(exception), retention));
    } catch (IOException | RuntimeException exception) {
      return ContractDecision.rejected(
          ContractErrors.artifactPublicationOutcomeUncertainFailure(
              normalizedPath, retention, NEW_BOOK_KEY_FILE_ARGUMENT));
    }
  }

  private static void createWitnessedFinalKeyLink(
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      Path finalPath,
      Path candidateStagePath)
      throws IOException {
    try {
      capabilityWitnesses.requireCurrent(
          finalPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    } catch (IOException exception) {
      throw new SqliteBookKeyFileFinalLinkAdmissionFailure(exception);
    }
    finalLinkCreator.create(finalPath, candidateStagePath);
  }

  /**
   * Generates a key directly into one fresh maintenance-owned stage without changing its access
   * control list or POSIX permissions by pathname.
   */
  static void generateIntoExistingOwnedStage(Path stagedPath) {
    Path normalizedStagePath = normalize(stagedPath);
    // Validate before producing secret bytes: an existing stage must already be proven owner-only
    // by its atomic creation path, never repaired after a writable pathname has been exposed.
    SqliteBookKeyFile.requireSecureKeyFile(normalizedStagePath).requireAccepted();
    byte[] encodedPassphrase = encodedPassphraseBytes();
    try {
      writeAndVerifyFile(normalizedStagePath, encodedPassphrase);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to generate the FinGrind maintenance key stage: "
              + SqliteMachinePaths.absoluteValue(normalizedStagePath),
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
    return Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath").toAbsolutePath().normalize();
  }

  private static Path requiredParent(Path normalizedPath) {
    return Objects.requireNonNull(normalizedPath.getParent(), "normalizedPath parent");
  }

  private static void requireAtomicPrivateStageCreation(Path normalizedPath) {
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(normalizedPath)) {
      return;
    }
    throw new SqliteCallerPathContractException(
        normalizedPath,
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        "The FinGrind generated book key file requires atomic owner-only private-stage creation.");
  }

  private static ContractFailure secretTargetOccupiedFailure(Path targetPath) {
    return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
        targetPath,
        "Generated secret target already exists and will not be overwritten.",
        "Choose an absent --new-book-key-file path or remove the existing file yourself before retrying.",
        NEW_BOOK_KEY_FILE_ARGUMENT);
  }

  private static void writeFile(Path normalizedPath, byte[] encodedPassphrase) throws IOException {
    try (FileChannel channel = SqliteSecureRegularFileAccess.openTruncatingWrite(normalizedPath)) {
      ByteBuffer bytes = ByteBuffer.wrap(encodedPassphrase);
      while (bytes.hasRemaining()) {
        if (channel.write(bytes) <= 0) {
          throw new IOException("Failed to write the complete FinGrind maintenance key stage.");
        }
      }
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
