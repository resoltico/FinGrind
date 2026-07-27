package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Canonical machine-readable deterministic error vocabulary for FinGrind CLI failures. */
public final class ContractErrors {
  private ContractErrors() {}

  /** Returns the canonical machine descriptors for every supported CLI error code. */
  public static List<ErrorDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /**
   * Preserves one primary deterministic failure while disclosing the immutable private stage that
   * existed before it was raised.
   */
  public static ContractFailure withRetainedArtifactStage(
      ContractFailure primaryFailure, ArtifactPublicationRetention retainedStage) {
    ContractFailure checkedPrimary =
        java.util.Objects.requireNonNull(primaryFailure, "primaryFailure");
    ArtifactPublicationRetention checkedRetention =
        java.util.Objects.requireNonNull(retainedStage, "retainedStage");
    if (checkedPrimary.retainedStage() != null
        && !checkedPrimary.retainedStage().equals(checkedRetention)) {
      throw new IllegalArgumentException(
          "A deterministic failure cannot report two different retained artifact stages.");
    }
    ContractFailurePaths paths =
        pathsIncludingRetainedStage(checkedPrimary.paths(), checkedRetention);
    return new ContractFailure(
        checkedPrimary.descriptor(),
        checkedPrimary.message(),
        checkedPrimary.hint(),
        checkedPrimary.argument(),
        paths,
        checkedPrimary.details(),
        checkedRetention);
  }

  /** Creates the one honest public failure for protected-book authentication or integrity loss. */
  public static ContractFailure protectedBookVerificationFailure() {
    return Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
        "FinGrind could not authenticate and verify the selected protected book.",
        "The supplied secret may be wrong, or the protected book may be damaged or tampered with. FinGrind cannot distinguish those causes before decryption. Confirm the intended book and secret; recover only from independently retained verified evidence.",
        null);
  }

  private static ContractFailurePaths pathsIncludingRetainedStage(
      @Nullable ContractFailurePaths existingPaths, ArtifactPublicationRetention retainedStage) {
    Path retainedStagePath = retainedStage.retainedStagePath();
    if (existingPaths == null) {
      return ContractFailurePaths.primary(retainedStagePath);
    }
    if (existingPaths.path().equals(retainedStagePath)
        || existingPaths.relatedPaths().contains(retainedStagePath)) {
      return existingPaths;
    }
    List<Path> relatedPaths = new java.util.ArrayList<>(existingPaths.relatedPaths());
    relatedPaths.add(retainedStagePath);
    return new ContractFailurePaths(existingPaths.path(), List.copyOf(relatedPaths));
  }

  /** Creates the public failure for an indeterminate no-replace-link attempt. */
  public static ContractFailure artifactPublicationOutcomeUncertainFailure(
      Path candidateArtifactPath,
      @Nullable ArtifactPublicationRetention retainedStage,
      String argument) {
    ContractFailureDetails.ArtifactPublicationOutcomeUncertain details =
        new ContractFailureDetails.ArtifactPublicationOutcomeUncertain(
            candidateArtifactPath, retainedStage);
    List<Path> relatedPaths =
        retainedStage == null ? List.of() : List.of(retainedStage.retainedStagePath());
    return new ContractFailure(
        Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
        "FinGrind could not establish whether its no-clobber artifact publication created the candidate final path.",
        "Preserve the reported candidate artifact and retained stage before retrying this no-clobber destination. Use a fresh destination for a new attempt.",
        argument,
        new ContractFailurePaths(details.candidateArtifactPath(), relatedPaths),
        details,
        retainedStage);
  }

  /**
   * Creates the one honest public failure when a final artifact link exists but its directory
   * durability could not be confirmed.
   */
  public static ContractFailure artifactPublicationDurabilityUncertainFailure(
      dev.erst.fingrind.core.ArtifactPublicationResult publication, String argument) {
    ContractFailureDetails.ArtifactPublicationDurabilityUncertain details =
        new ContractFailureDetails.ArtifactPublicationDurabilityUncertain(publication);
    return new ContractFailure(
        Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
        "The requested artifact was published, but FinGrind could not confirm its directory durability.",
        "Preserve the reported artifact and retained stage, inspect the artifact before relying on it, and do not retry this no-clobber target.",
        argument,
        new ContractFailurePaths(
            details.publication().publishedArtifactPath(),
            List.of(details.publication().retention().retainedStagePath())),
        details,
        details.publication().retention());
  }

  /** Creates the recovery-required failure for a completion-uncertain protected-book pair. */
  public static ContractFailure protectedBookPairPublicationUncertainFailure(
      dev.erst.fingrind.contract.protocol.OperationId operation,
      ContractFailureDetails.PairPublication pairPublication) {
    ContractFailureDetails.ProtectedBookPairPublicationUncertain details =
        new ContractFailureDetails.ProtectedBookPairPublicationUncertain(
            operation, pairPublication);
    return new ContractFailure(
        Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        "FinGrind could not confirm durable completion of the protected-book pair publication.",
        "Preserve FinGrind pair evidence and both reported final paths. Rerun "
            + details.operation().wireName()
            + " with its complete original inputs, including exactly the reported final paths, so FinGrind can verify and recover the pair. Do not rename, overwrite, delete, recreate, or manually clean the pair evidence or either final member. For a recovered rekey, FinGrind"
            + " verifies the generated-key pair before it attempts any prior-key access. When"
            + " recoveryRecordState is present, preserve FinGrind's recovery material too.",
        null,
        ContractPairPublicationPaths.forPairPublication(details.pairPublication()),
        details,
        null);
  }

  /** Creates the public failure for pair evidence that blocks a safe publication decision. */
  public static ContractFailure protectedBookPairPublicationEvidenceBlockedFailure(
      ContractFailureDetails.PairPublication pairPublication) {
    ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked details =
        new ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked(pairPublication);
    return new ContractFailure(
        Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        "FinGrind found protected-book pair evidence that cannot establish a safe final-member publication state.",
        "Preserve FinGrind pair evidence and both reported final paths. Do not rerun, rename, overwrite, delete, recreate, or manually clean either final member or the evidence until the retained evidence has been independently investigated.",
        null,
        ContractPairPublicationPaths.forPairPublication(details.pairPublication()),
        details,
        null);
  }

  /** Creates the exact public failure for a valid FinGrind book at a non-current format. */
  public static ContractFailure unsupportedBookFormatVersionFailure(
      int detectedBookFormatVersion, int supportedBookFormatVersion) {
    ContractFailureDetails.UnsupportedBookFormatVersion details =
        new ContractFailureDetails.UnsupportedBookFormatVersion(
            detectedBookFormatVersion, supportedBookFormatVersion);
    return new ContractFailure(
        Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION,
        "The selected FinGrind book uses format version "
            + details.detectedBookFormatVersion()
            + ", but this FinGrind binary supports version "
            + details.supportedBookFormatVersion()
            + " only.",
        "Use a FinGrind binary that supports the selected book's exact format version. FinGrind neither migrates nor opens non-current formats.",
        "--book-file",
        null,
        details,
        null);
  }

  /** Creates the only truthful error when failed book opening retains immutable artifacts. */
  public static ContractFailure openBookPreparationArtifactsRetainedFailure(
      List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts) {
    OpenBookFailureDetails.OpenBookPreparationArtifactsRetained details =
        new OpenBookFailureDetails.OpenBookPreparationArtifactsRetained(retainedArtifacts);
    Set<Path> locations = new LinkedHashSet<>();
    for (OpenBookFailureDetails.RetainedOpenBookPreparationArtifact artifact :
        details.retainedArtifacts()) {
      locations.add(artifact.path());
      if (artifact.retainedStage() != null) {
        locations.add(artifact.retainedStage().retainedStagePath());
      }
    }
    List<Path> paths = List.copyOf(locations);
    return new ContractFailure(
        Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
        "Book opening did not complete, and FinGrind retained every artifact it created as immutable evidence.",
        "Preserve every reported path. Do not rename, overwrite, delete, recreate, or reuse it; choose fresh paths before retrying "
            + OperationId.OPEN_BOOK.wireName()
            + ".",
        null,
        new ContractFailurePaths(paths.get(0), paths.subList(1, paths.size())),
        details,
        null);
  }

  /**
   * Creates the only truthful response when initialization returned its facts but SQLite could not
   * confirm durable completion after its COMMIT acknowledgement or session shutdown.
   */
  public static ContractFailure openBookCompletionUncertainFailure(
      OpenBookFailureDetails.OpenBookCompletionUncertain details) {
    OpenBookFailureDetails.OpenBookCompletionUncertain checkedDetails =
        java.util.Objects.requireNonNull(details, "details");
    Set<Path> locations = new LinkedHashSet<>();
    locations.add(checkedDetails.bookFilePath());
    for (OpenBookFailureDetails.RetainedOpenBookPreparationArtifact artifact :
        checkedDetails.retainedBookArtifacts()) {
      locations.add(artifact.path());
    }
    for (dev.erst.fingrind.core.ArtifactPublicationResult founderKey :
        checkedDetails.retainedFounderKeyArtifacts()) {
      locations.add(founderKey.publishedArtifactPath());
      locations.add(founderKey.retention().retainedStagePath());
    }
    List<Path> paths = List.copyOf(locations);
    return new ContractFailure(
        Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN,
        "FinGrind returned book-opening facts, but SQLite could not confirm durable completion after initialization COMMIT or session shutdown.",
        "Do not retry this --book-file destination. Inspect and verify the reported book and attestation head before relying on it or taking recovery action.",
        "--book-file",
        new ContractFailurePaths(paths.get(0), paths.subList(1, paths.size())),
        checkedDetails,
        null);
  }

  /** Stable descriptor for a deterministic CLI error code. */
  public enum Descriptor {
    UNKNOWN_COMMAND,
    INVALID_REQUEST,
    INTERNAL_ERROR,
    INTERNAL_DEFECT,
    MANAGED_RUNTIME_FAILURE,
    STORAGE_RUNTIME_FAILURE,
    ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
    ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
    PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
    PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
    PDF_EXPORT_FAILURE,
    INVALID_PAGE_CURSOR,
    SECRET_TARGET_OCCUPIED,
    BOOK_DESTINATION_OCCUPIED,
    ARTIFACT_OUTPUT_ALREADY_EXISTS,
    INVALID_ARTIFACT_OUTPUT_DIRECTORY,
    INVALID_BOOK_KEY_FILE,
    INVALID_BOOK_FILE_PATH,
    INVALID_BOOK_PASSPHRASE_SOURCE,
    INVALID_ATTESTATION_CREDENTIAL,
    ATTESTATION_CREDENTIALS_NOT_ALLOWED,
    INVALID_ATTESTATION_KEY_FILE,
    OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
    OPEN_BOOK_COMPLETION_UNCERTAIN,
    STALE_HEAD,
    ATTESTATION_REVIEW_REQUIRED,
    ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD,
    BOOK_MAINTENANCE_IN_PROGRESS,
    INTERACTIVE_PROMPT_UNAVAILABLE,
    INTERACTIVE_PROMPT_FAILED,
    UNSUPPORTED_OUTPUT_SELECTION,
    CUSTODIAN_NOT_SUPPORTED,
    PROTECTED_BOOK_VERIFICATION_FAILED,
    UNSUPPORTED_BOOK_FORMAT_VERSION;

    /** Returns the stable wire code for this deterministic error descriptor. */
    public String code() {
      return definition().code();
    }

    /** Returns the canonical machine-readable description for this error descriptor. */
    public String description() {
      return definition().description();
    }

    /** Returns the transport category declared for this deterministic error. */
    public FailureCategory category() {
      return definition().category();
    }

    /** Returns the canonical process exit code for this deterministic error descriptor. */
    public int exitCode() {
      return definition().exitCode();
    }

    /** Creates a deterministic failure with this canonical contract descriptor. */
    public ContractFailure failure(
        String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(this, message, hint, argument, null, null, null);
    }

    /** Creates a deterministic failure anchored to one real filesystem location. */
    public ContractFailure failureAt(
        Path path, String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(
          this, message, hint, argument, ContractFailurePaths.primary(path), null, null);
    }

    private ContractErrorDescriptorDefinition definition() {
      return ContractErrorDescriptorCatalog.definitionFor(this);
    }

    private ErrorDescriptor descriptor() {
      ContractErrorDescriptorDefinition definition = definition();
      List<FieldDescriptor> detailFields = ContractErrorDetailFields.forDescriptor(this);
      return detailFields.isEmpty()
          ? new ErrorDescriptor(
              definition.code(),
              definition.category(),
              definition.exitCode(),
              definition.description())
          : new ErrorDescriptor(
              definition.code(),
              definition.category(),
              definition.exitCode(),
              definition.description(),
              detailFields);
    }

    private static List<ErrorDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
