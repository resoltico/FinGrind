package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.executor.AttestationGenesisPreparation;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns the private artifacts retained when an open-book attempt cannot complete. */
final class CliOpenBookArtifactCustody {
  private CliOpenBookArtifactCustody() {}

  static @Nullable ContractFailure occupiedBookDestinationFailure(Path bookFilePath) {
    Path normalizedBookFilePath = bookFilePath.toAbsolutePath().normalize();
    if (!Files.exists(normalizedBookFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    return ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED.failure(
        "The selected --book-file destination already exists; "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + " will not access or replace it.",
        "Choose a missing --book-file destination before opening a new book.",
        ProtocolBookAccessOptions.BOOK_FILE);
  }

  static ContractFailure preparationArtifactsRetainedFailure(
      List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts) {
    return ContractErrors.openBookPreparationArtifactsRetainedFailure(retainedArtifacts);
  }

  static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedOpeningArtifacts(
      Path bookFilePath, AttestationGenesisPreparation preparation) {
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts =
        new java.util.ArrayList<>(
            Objects.requireNonNull(preparation, "preparation")
                .retainedFounderKeyArtifacts()
                .stream()
                .map(OpenBookFailureDetails.RetainedOpenBookPreparationArtifact::founderKey)
                .toList());
    retainedArtifacts.addAll(retainedNewBookArtifacts(bookFilePath));
    return List.copyOf(retainedArtifacts);
  }

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
