package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Owns witnessed final-target publication and durability confirmation for generated key files. */
final class SqliteBookKeyFilePublicationWorkflow {
  private static final String NEW_BOOK_KEY_FILE_ARGUMENT = "--new-book-key-file";

  private SqliteBookKeyFilePublicationWorkflow() {}

  /** Publishes material whose in-memory lifecycle remains owned by the generation workflow. */
  static ContractDecision<GeneratedBookKeyFile> publishDecision(
      Path normalizedPath,
      byte[] encodedPassphrase,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer,
      SqliteBookKeyFileGenerator.RetainedStageCreator retainedStageCreator,
      SqliteBookKeyFileGenerator.PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer) {
    try {
      return generatePublication(
          normalizedPath,
          encodedPassphrase,
          finalLinkCreator,
          parentDirectoryForcer,
          retainedStageCreator,
          capabilityWitnessAcquirer);
    } catch (SqliteBookKeyFileRetainedStageMaterializationFailure exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
                  normalizedPath,
                  "FinGrind could not materialize a private owner-only stage for the requested book"
                      + " key file.",
                  "Preserve the reported retained stage, inspect the selected key-file parent"
                      + " directory and filesystem, then choose a fresh --new-book-key-file"
                      + " destination before retrying.",
                  NEW_BOOK_KEY_FILE_ARGUMENT),
              exception.retention()));
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(exception.targetPath()));
    } catch (ContractFailureException exception) {
      return ContractDecision.rejected(exception.failure());
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to validate the private parent directory for the FinGrind book key file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    }
  }

  private static ContractDecision<GeneratedBookKeyFile> generatePublication(
      Path normalizedPath,
      byte[] encodedPassphrase,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer,
      SqliteBookKeyFileGenerator.RetainedStageCreator retainedStageCreator,
      SqliteBookKeyFileGenerator.PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer)
      throws IOException {
    // Check occupancy before parent validation so an existing root path still reports its actual
    // no-overwrite refusal rather than a synthetic parent-path error.
    SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
    SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(normalizedPath);
    SqliteGeneratedSecretTarget secretTarget =
        SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
    return switch (acquireWitnesses(normalizedPath, capabilityWitnessAcquirer)) {
      case WitnessesUnavailable(SqlitePublicationCapabilityWitness.AcquisitionFailure failure) ->
          rejectedWitnessAcquisition(normalizedPath, failure);
      case WitnessesAvailable(SqlitePublicationCapabilityWitness.Set capabilityWitnesses) ->
          generateWithWitnesses(
              secretTarget,
              normalizedPath,
              encodedPassphrase,
              finalLinkCreator,
              parentDirectoryForcer,
              retainedStageCreator,
              capabilityWitnesses);
    };
  }

  private static ContractDecision<GeneratedBookKeyFile> generateWithWitnesses(
      SqliteGeneratedSecretTarget secretTarget,
      Path normalizedPath,
      byte[] encodedPassphrase,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer,
      SqliteBookKeyFileGenerator.RetainedStageCreator retainedStageCreator,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    ContractDecision<GeneratedBookKeyFile> decision;
    try {
      decision =
          generateWithOpenWitnesses(
              secretTarget,
              normalizedPath,
              encodedPassphrase,
              finalLinkCreator,
              parentDirectoryForcer,
              retainedStageCreator,
              capabilityWitnesses);
    } catch (RuntimeException | Error failure) {
      SqliteRuntimeCloseSequence.closeAllPreservingFailure(
          List.of(capabilityWitnesses::close), failure);
      throw failure;
    }
    capabilityWitnesses.close();
    return decision;
  }

  /** Publishes through an already-open witness set without taking ownership of its closure. */
  private static ContractDecision<GeneratedBookKeyFile> generateWithOpenWitnesses(
      SqliteGeneratedSecretTarget secretTarget,
      Path normalizedPath,
      byte[] encodedPassphrase,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer,
      SqliteBookKeyFileGenerator.RetainedStageCreator retainedStageCreator,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
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
    return forcePublicationDirectory(normalizedPath, publication, parentDirectoryForcer);
  }

  private static ContractDecision<GeneratedBookKeyFile> forcePublicationDirectory(
      Path normalizedPath,
      ArtifactPublicationResult publication,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer) {
    try {
      parentDirectoryForcer.force(
          SqliteBookKeyFileMaterial.requiredParent(publication.publishedArtifactPath()));
    } catch (IOException | RuntimeException exception) {
      return ContractDecision.rejected(
          ContractErrors.artifactPublicationDurabilityUncertainFailure(
              publication, NEW_BOOK_KEY_FILE_ARGUMENT));
    }
    return ContractDecision.accepted(
        new GeneratedBookKeyFile(
            publication,
            SqliteBookKeyFileGenerator.GENERATED_ENCODING,
            SqliteBookKeyFileGenerator.GENERATED_ENTROPY_BITS,
            SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath)));
  }

  private static WitnessAcquisition acquireWitnesses(
      Path normalizedPath,
      SqliteBookKeyFileGenerator.PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer) {
    try {
      return new WitnessesAvailable(capabilityWitnessAcquirer.acquire(normalizedPath));
    } catch (SqlitePublicationCapabilityWitness.AcquisitionFailure failure) {
      return new WitnessesUnavailable(failure);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to establish the retained FinGrind generated-secret publication witness: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    }
  }

  private static ContractDecision<GeneratedBookKeyFile> rejectedWitnessAcquisition(
      Path normalizedPath, SqlitePublicationCapabilityWitness.AcquisitionFailure failure) {
    @org.jspecify.annotations.Nullable SqliteCallerPathContractException pathFailure =
        SqlitePublicationCapabilityWitness.callerPathFailure(
            failure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED);
    if (pathFailure != null) {
      return ContractDecision.rejected(
          SqliteCallerPathFailureMapper.invalidBookKeyFile(pathFailure));
    }
    throw new IllegalStateException(
        "Failed to establish the retained FinGrind generated-secret publication witness: "
            + SqliteMachinePaths.absoluteValue(normalizedPath),
        failure);
  }

  /** Closed result of witness acquisition before any secret material is staged. */
  private sealed interface WitnessAcquisition permits WitnessesAvailable, WitnessesUnavailable {}

  /**
   * Holds the retained witness set that will close after staged publication reaches a terminal
   * fact.
   */
  private record WitnessesAvailable(SqlitePublicationCapabilityWitness.Set witnesses)
      implements WitnessAcquisition {
    private WitnessesAvailable {
      Objects.requireNonNull(witnesses, "witnesses");
    }
  }

  /** Holds the classified acquisition failure that a caller may translate into a path rejection. */
  private record WitnessesUnavailable(SqlitePublicationCapabilityWitness.AcquisitionFailure failure)
      implements WitnessAcquisition {
    private WitnessesUnavailable {
      Objects.requireNonNull(failure, "failure");
    }
  }

  static SqlitePublicationCapabilityWitness.Set acquirePublicationCapabilityWitness(
      Path normalizedPath) throws IOException {
    return SqlitePublicationCapabilityWitness.acquire(
        List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(normalizedPath)),
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
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
          (finalPath, candidateStagePath) ->
              createWitnessedFinalKeyLink(
                  capabilityWitnesses, finalLinkCreator, finalPath, candidateStagePath));
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
                  "FinGrind could not confirm that the selected book-key target remained eligible"
                      + " for atomic no-replace publication.",
                  "Preserve the reported retained stage, inspect the selected target and its"
                      + " private parent directory, then choose a fresh --new-book-key-file"
                      + " destination before retrying.",
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

  private static ContractFailure secretTargetOccupiedFailure(Path targetPath) {
    return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
        targetPath,
        "Generated secret target already exists and will not be overwritten.",
        "Choose an absent --new-book-key-file path or remove the existing file yourself before"
            + " retrying.",
        NEW_BOOK_KEY_FILE_ARGUMENT);
  }
}
