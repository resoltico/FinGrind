package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import java.util.List;

/** Renders artifact-publication details without conflating known and indeterminate paths. */
final class CliArtifactPublicationErrorDetailsTextRenderer {
  private CliArtifactPublicationErrorDetailsTextRenderer() {}

  static void appendPublicationOutcomeUncertainRows(
      List<List<String>> rows,
      CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails details) {
    rows.add(
        List.of(
            "Candidate artifact path",
            CliTextDisplay.serializedAbsolutePath(details.candidateArtifact())));
    if (details.retainedStage() != null) {
      appendRetainedStageRow(rows, details.retainedStage(), "Retained stage path");
    }
  }

  static void appendPublicationDurabilityUncertainRows(
      List<List<String>> rows,
      CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails details) {
    CliMaintenanceErrorJsonModels.PublishedArtifact publishedArtifact = details.publishedArtifact();
    rows.add(
        List.of(
            "Published artifact", CliTextDisplay.serializedAbsolutePath(publishedArtifact.path())));
    appendRetainedStageRow(rows, publishedArtifact.retainedStage(), "Retained stage path");
  }

  private static void appendRetainedStageRow(
      List<List<String>> rows, String retainedStage, String stagePathLabel) {
    rows.add(List.of(stagePathLabel, CliTextDisplay.serializedAbsolutePath(retainedStage)));
  }
}
