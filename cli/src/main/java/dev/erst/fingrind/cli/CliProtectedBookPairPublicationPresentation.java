package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps final-only protected-book pair publication facts consistently across JSON and text. */
final class CliProtectedBookPairPublicationPresentation {
  private static final String NO_FIN_GRIND_PUBLICATION =
      "No FinGrind publication transaction (already-published acknowledgement)";

  private CliProtectedBookPairPublicationPresentation() {}

  static CliBookPairPublicationJsonModels.@Nullable PairPublicationPayload payload(
      @Nullable ProtectedBookPairPublication publication) {
    if (publication == null) {
      return null;
    }
    ProtectedBookPairPublication checkedPublication =
        Objects.requireNonNull(publication, "publication");
    return new CliBookPairPublicationJsonModels.PairPublicationPayload(
        new CliBookPairPublicationJsonModels.PairPublicationMemberPayload(
            CliPublicPaths.absoluteValue(
                checkedPublication.bookPublication().publishedArtifactPath())),
        new CliBookPairPublicationJsonModels.PairPublicationMemberPayload(
            CliPublicPaths.absoluteValue(
                checkedPublication.generatedSecretPublication().publishedArtifactPath())),
        CliEnvelopeMapper.publicationTransaction(checkedPublication.publicationTransaction()));
  }

  static void appendTextRows(
      List<List<String>> rows, @Nullable ProtectedBookPairPublication publication) {
    List<List<String>> checkedRows = Objects.requireNonNull(rows, "rows");
    if (publication == null) {
      checkedRows.add(List.of("Pair publication facts", NO_FIN_GRIND_PUBLICATION));
      return;
    }
    ProtectedBookPairPublication checkedPublication =
        Objects.requireNonNull(publication, "publication");
    checkedRows.add(
        List.of(
            "Published book file",
            CliTextDisplay.path(checkedPublication.bookPublication().publishedArtifactPath())));
    checkedRows.add(
        List.of(
            "Published generated-secret file",
            CliTextDisplay.path(
                checkedPublication.generatedSecretPublication().publishedArtifactPath())));
    checkedRows.add(
        List.of(
            "Publication transaction",
            checkedPublication.publicationTransaction().transactionId().value()));
  }

  static void appendTextRows(
      List<List<String>> rows,
      CliBookPairPublicationJsonModels.@Nullable PairPublicationPayload publication) {
    List<List<String>> checkedRows = Objects.requireNonNull(rows, "rows");
    if (publication == null) {
      checkedRows.add(List.of("Pair publication facts", NO_FIN_GRIND_PUBLICATION));
      return;
    }
    checkedRows.add(
        List.of(
            "Published book file",
            CliTextDisplay.serializedAbsolutePath(publication.bookPublication().path())));
    checkedRows.add(
        List.of(
            "Published generated-secret file",
            CliTextDisplay.serializedAbsolutePath(
                publication.generatedSecretPublication().path())));
    checkedRows.add(List.of("Publication transaction", publication.publicationTransaction().id()));
  }
}
