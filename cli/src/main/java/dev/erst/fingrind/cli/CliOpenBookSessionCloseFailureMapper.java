package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Preserves open-book reconciliation facts when the exclusive new-book session cannot close. */
final class CliOpenBookSessionCloseFailureMapper {
  private final Path bookFilePath;

  CliOpenBookSessionCloseFailureMapper(Path bookFilePath) {
    this.bookFilePath = Objects.requireNonNull(bookFilePath, "bookFilePath");
  }

  ContractDecision<OpenBookResult> rejectedSessionCloseFailure(
      ContractFailure rejection, RuntimeException closeFailure) {
    Objects.requireNonNull(closeFailure, "closeFailure");
    return ContractDecision.rejected(retainedNewBookSessionFailure(rejection));
  }

  /** Preserves the already-created new-book namespace before returning any opening rejection. */
  ContractFailure retainedNewBookSessionFailure(ContractFailure rejection) {
    ContractFailure checkedRejection = Objects.requireNonNull(rejection, "rejection");
    return ContractErrors.openBookPreparationArtifactsRetainedFailure(
        mergedRetainedArtifacts(
            openBookPreparationArtifacts(checkedRejection),
            retainedNewBookArtifacts(bookFilePath)));
  }

  ContractDecision<OpenBookResult> workAndSessionCloseFailure(
      RuntimeException workFailure, RuntimeException closeFailure) {
    Objects.requireNonNull(workFailure, "workFailure");
    Objects.requireNonNull(closeFailure, "closeFailure");
    return ContractDecision.rejected(
        ContractErrors.openBookPreparationArtifactsRetainedFailure(
            retainedNewBookArtifacts(bookFilePath)));
  }

  ContractDecision<OpenBookResult> acceptedSessionCloseFailure(
      OpenBookResult result, RuntimeException closeFailure) {
    Objects.requireNonNull(closeFailure, "closeFailure");
    return switch (Objects.requireNonNull(result, "result")) {
      case OpenBookResult.Opened opened ->
          ContractDecision.rejected(
              ContractErrors.openBookCompletionUncertainFailure(
                  new OpenBookFailureDetails.OpenBookCompletionUncertain(
                      bookFilePath,
                      opened.initializedAt(),
                      opened.bookIdentity(),
                      opened.attestationTrustRoot(),
                      opened.attestationCommit(),
                      opened.retainedFounderKeyArtifacts(),
                      retainedNewBookArtifacts(bookFilePath))));
      case OpenBookResult.Rejected _ ->
          ContractDecision.rejected(
              ContractErrors.openBookPreparationArtifactsRetainedFailure(
                  retainedNewBookArtifacts(bookFilePath)));
    };
  }

  private static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact>
      openBookPreparationArtifacts(ContractFailure rejection) {
    if (rejection.details()
        instanceof OpenBookFailureDetails.OpenBookPreparationArtifactsRetained retained) {
      return retained.retainedArtifacts();
    }
    if (rejection.details()
        instanceof ContractFailureDetails.ArtifactPublicationOutcomeUncertain outcomeUncertain) {
      return List.of(
          OpenBookFailureDetails.retainedArtifact(
              OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
              outcomeUncertain.candidateArtifactPath(),
              outcomeUncertain.retainedStage()));
    }
    if (rejection.retainedStage() != null) {
      return List.of(
          OpenBookFailureDetails.retainedArtifact(
              OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY_STAGE,
              rejection.retainedStage().retainedStagePath(),
              rejection.retainedStage()));
    }
    return List.of();
  }

  private static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact>
      mergedRetainedArtifacts(
          List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> first,
          List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> second) {
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> artifacts =
        new ArrayList<>(first);
    second.stream()
        .filter(
            secondArtifact ->
                artifacts.stream()
                    .noneMatch(firstArtifact -> firstArtifact.path().equals(secondArtifact.path())))
        .forEach(artifacts::add);
    return List.copyOf(artifacts);
  }

  /**
   * Lists every SQLite name an uninitialized exclusive opening can have created. A close failure
   * leaves every created name as retained evidence even when a live namespace lookup happens to
   * find none.
   */
  private static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact>
      retainedNewBookArtifacts(Path bookFilePath) {
    Path normalizedBookPath =
        Objects.requireNonNull(bookFilePath, "bookFilePath").toAbsolutePath().normalize();
    String fileName =
        Objects.requireNonNull(normalizedBookPath.getFileName(), "book file name").toString();
    return List.of(
        OpenBookFailureDetails.retainedArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
            normalizedBookPath,
            null),
        OpenBookFailureDetails.retainedArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
            normalizedBookPath.resolveSibling(fileName + "-journal"),
            null),
        OpenBookFailureDetails.retainedArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
            normalizedBookPath.resolveSibling(fileName + "-wal"),
            null),
        OpenBookFailureDetails.retainedArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
            normalizedBookPath.resolveSibling(fileName + "-shm"),
            null));
  }
}
