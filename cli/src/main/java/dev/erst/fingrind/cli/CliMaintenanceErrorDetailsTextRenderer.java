package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import java.util.List;

/** Renders the recovery evidence shared by publication and protected-book maintenance errors. */
final class CliMaintenanceErrorDetailsTextRenderer {
  private CliMaintenanceErrorDetailsTextRenderer() {}

  static void appendRows(
      List<List<String>> rows, CliMaintenanceErrorJsonModels.MaintenanceErrorDetails details) {
    switch (details) {
      case CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails value ->
          CliArtifactPublicationErrorDetailsTextRenderer.appendPublicationTransactionIncompleteRows(
              rows, value);
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
    }
  }
}
