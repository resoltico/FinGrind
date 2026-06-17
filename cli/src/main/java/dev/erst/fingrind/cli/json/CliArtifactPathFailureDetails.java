package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

/** Machine-readable maintenance rejection details for one invalid artifact path. */
public record CliArtifactPathFailureDetails(
    String artifactRole, String artifactPath, String pathFailure)
    implements CliRejectionJsonModels.MaintenanceRejectionDetails {
  public CliArtifactPathFailureDetails {
    artifactRole = requireText(artifactRole, "artifactRole");
    artifactPath = requireText(artifactPath, "artifactPath");
    pathFailure = requireText(pathFailure, "pathFailure");
  }
}
