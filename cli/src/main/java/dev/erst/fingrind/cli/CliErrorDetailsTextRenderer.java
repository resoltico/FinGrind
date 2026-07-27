package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Dispatches structured error-detail families to their semantic text renderers. */
final class CliErrorDetailsTextRenderer {
  private CliErrorDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows, CliErrorJsonModels.@Nullable ErrorDetails details) {
    if (details == null) {
      return;
    }
    switch (details) {
      case CliErrorJsonModels.InvalidJsonDetails value ->
          CliBasicErrorDetailsTextRenderer.appendInvalidJsonRows(rows, value);
      case CliErrorJsonModels.InvalidRequestDetails value ->
          CliBasicErrorDetailsTextRenderer.appendInvalidRequestRows(rows, value);
      case CliErrorJsonModels.StaleHeadDetails value ->
          CliBasicErrorDetailsTextRenderer.appendStaleHeadRows(rows, value);
      case CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails value ->
          CliArtifactPublicationErrorDetailsTextRenderer.appendPublicationOutcomeUncertainRows(
              rows, value);
      case CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails value ->
          CliArtifactPublicationErrorDetailsTextRenderer.appendPublicationDurabilityUncertainRows(
              rows, value);
      case CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails value ->
          CliProtectedBookPairPublicationErrorDetailsTextRenderer.appendRows(rows, value);
      case CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationEvidenceBlockedDetails value ->
          CliProtectedBookPairPublicationErrorDetailsTextRenderer.appendRows(
              rows, value.pairPublication());
      case CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails value ->
          CliOpenBookErrorDetailsTextRenderer.appendRetainedArtifactRows(rows, value);
      case CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails value ->
          CliOpenBookErrorDetailsTextRenderer.appendCompletionRows(rows, value);
      case CliErrorJsonModels.AttestationReviewWindowDetails value ->
          CliBasicErrorDetailsTextRenderer.appendReviewWindowRows(rows, value);
      case CliErrorJsonModels.UnsupportedBookFormatVersionDetails value ->
          CliBasicErrorDetailsTextRenderer.appendUnsupportedBookFormatRows(rows, value);
    }
  }

  /** Returns whether the structured error details already render every failure path by role. */
  static boolean rendersFailurePaths(CliErrorJsonModels.@Nullable ErrorDetails details) {
    return details
            instanceof CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails
        || details
            instanceof CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails
        || details
            instanceof CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails
        || details
            instanceof
            CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationEvidenceBlockedDetails
        || details instanceof CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails
        || details instanceof CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails;
  }
}
