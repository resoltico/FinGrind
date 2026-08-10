package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails.PublicationTransactionIncomplete;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Builds the protected-book opening failures that preserve distinct publication evidence. */
final class ContractOpenBookFailures {
  private ContractOpenBookFailures() {}

  static ContractFailure preparationArtifactsRetained(
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
    return failure(
        ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
        "Book opening did not complete, and FinGrind retained every artifact it created as immutable evidence.",
        "Preserve every reported path. Do not rename, overwrite, delete, recreate, or reuse it; choose fresh paths before retrying "
            + OperationId.OPEN_BOOK.wireName()
            + ".",
        null,
        locations,
        details);
  }

  static ContractFailure publicationProgress(
      List<PublicationTransactionArtifact> publishedFounderKeyArtifacts,
      @Nullable PublicationTransactionIncomplete incompleteFounderKeyPublication) {
    OpenBookFailureDetails.OpenBookPublicationProgress details =
        new OpenBookFailureDetails.OpenBookPublicationProgress(
            publishedFounderKeyArtifacts, incompleteFounderKeyPublication);
    Set<Path> locations = new LinkedHashSet<>();
    for (PublicationTransactionArtifact artifact : details.publishedFounderKeyArtifacts()) {
      locations.add(artifact.publishedArtifactPath());
    }
    if (details.incompleteFounderKeyPublication() != null) {
      locations.add(details.incompleteFounderKeyPublication().candidateArtifactPath());
    }
    return failure(
        ContractErrors.Descriptor.OPEN_BOOK_PUBLICATION_PROGRESS,
        "Book opening did not complete after FinGrind recorded founder-key publication progress.",
        "Preserve every reported final artifact. Inspect completed or incomplete founder-key"
            + " publication only through each reported transaction identifier; do not alter private"
            + " output directories or retry any final destination manually.",
        null,
        locations,
        details);
  }

  static ContractFailure completionUncertain(
      OpenBookFailureDetails.OpenBookCompletionUncertain details) {
    OpenBookFailureDetails.OpenBookCompletionUncertain checkedDetails =
        java.util.Objects.requireNonNull(details, "details");
    Set<Path> locations = new LinkedHashSet<>();
    locations.add(checkedDetails.bookFilePath());
    for (OpenBookFailureDetails.RetainedOpenBookPreparationArtifact artifact :
        checkedDetails.retainedBookArtifacts()) {
      locations.add(artifact.path());
    }
    for (PublicationTransactionArtifact founderKey :
        checkedDetails.publishedFounderKeyArtifacts()) {
      locations.add(founderKey.publishedArtifactPath());
    }
    return failure(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN,
        "FinGrind returned book-opening facts, but SQLite could not confirm durable completion after initialization COMMIT or session shutdown.",
        "Do not retry this --book-file destination. Inspect and verify the reported book and attestation head before relying on it or taking recovery action.",
        "--book-file",
        locations,
        checkedDetails);
  }

  private static ContractFailure failure(
      ContractErrors.Descriptor descriptor,
      String message,
      String hint,
      @Nullable String argument,
      Set<Path> locations,
      ContractFailureDetails details) {
    List<Path> paths = List.copyOf(locations);
    return new ContractFailure(
        descriptor,
        message,
        hint,
        argument,
        new ContractFailurePaths(paths.getFirst(), paths.subList(1, paths.size())),
        details,
        null);
  }
}
