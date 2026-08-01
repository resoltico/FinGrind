package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps authoritative protected-book pair publication facts consistently across JSON and text. */
final class CliProtectedBookPairPublicationRetentionPresentation {
  private static final String NO_FIN_GRIND_RETAINED_STAGE_EVIDENCE =
      "No FinGrind retained-stage evidence (already-published acknowledgement)";

  private CliProtectedBookPairPublicationRetentionPresentation() {}

  static CliBookPairPublicationJsonModels.@Nullable PairPublicationRetentionPayload payload(
      @Nullable ProtectedBookPairPublicationRetention retention) {
    if (retention == null) {
      return null;
    }
    return new CliBookPairPublicationJsonModels.PairPublicationRetentionPayload(
        publicationPayload(retention.bookPublication()),
        publicationPayload(retention.generatedSecretPublication()));
  }

  static void appendTextRows(
      List<List<String>> rows, @Nullable ProtectedBookPairPublicationRetention retention) {
    List<List<String>> checkedRows = Objects.requireNonNull(rows, "rows");
    if (retention == null) {
      checkedRows.add(List.of("Pair publication facts", NO_FIN_GRIND_RETAINED_STAGE_EVIDENCE));
      return;
    }
    appendPublicationRows(
        checkedRows,
        CliTextDisplay.path(retention.bookPublication().publishedArtifactPath()),
        CliTextDisplay.path(retention.bookPublication().retention().retainedStagePath()),
        CliTextDisplay.path(retention.generatedSecretPublication().publishedArtifactPath()),
        CliTextDisplay.path(
            retention.generatedSecretPublication().retention().retainedStagePath()));
  }

  static void appendTextRows(
      List<List<String>> rows,
      CliBookPairPublicationJsonModels.@Nullable PairPublicationRetentionPayload retention) {
    List<List<String>> checkedRows = Objects.requireNonNull(rows, "rows");
    if (retention == null) {
      checkedRows.add(List.of("Pair publication facts", NO_FIN_GRIND_RETAINED_STAGE_EVIDENCE));
      return;
    }
    appendPublicationRows(
        checkedRows,
        CliTextDisplay.serializedAbsolutePath(retention.bookPublication().path()),
        CliTextDisplay.serializedAbsolutePath(retention.bookPublication().retainedStage()),
        CliTextDisplay.serializedAbsolutePath(retention.generatedSecretPublication().path()),
        CliTextDisplay.serializedAbsolutePath(
            retention.generatedSecretPublication().retainedStage()));
  }

  private static CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload
      publicationPayload(dev.erst.fingrind.core.ArtifactPublicationResult publication) {
    dev.erst.fingrind.core.ArtifactPublicationResult checkedPublication =
        Objects.requireNonNull(publication, "publication");
    return new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
        CliPublicPaths.absoluteValue(checkedPublication.publishedArtifactPath()),
        CliPublicPaths.absoluteValue(checkedPublication.retention().retainedStagePath()));
  }

  private static void appendPublicationRows(
      List<List<String>> rows,
      String bookPublicationPath,
      String bookRetainedStage,
      String generatedSecretPublicationPath,
      String generatedSecretRetainedStage) {
    rows.add(List.of("Published book file", bookPublicationPath));
    rows.add(List.of("Book retained stage", bookRetainedStage));
    rows.add(List.of("Published generated-secret file", generatedSecretPublicationPath));
    rows.add(List.of("Generated-secret retained stage", generatedSecretRetainedStage));
  }
}
