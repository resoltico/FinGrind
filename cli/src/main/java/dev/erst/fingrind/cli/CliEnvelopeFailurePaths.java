package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Extracts one canonical primary path and related paths from typed maintenance failure details. */
record CliEnvelopeFailurePaths(String path, List<String> relatedPaths) {
  CliEnvelopeFailurePaths {
    java.util.Objects.requireNonNull(path, "path");
    relatedPaths = List.copyOf(java.util.Objects.requireNonNull(relatedPaths, "relatedPaths"));
  }

  static @Nullable CliEnvelopeFailurePaths from(
      CliEnvelopeJsonModels.@Nullable EnvelopeDetails details) {
    if (!(details
        instanceof CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails maintenance)) {
      return null;
    }
    return switch (maintenance) {
      case CliMaintenanceRejectionJsonModels.BookFileDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), List.of());
      case CliMaintenanceRejectionJsonModels.BookAndBackupFileDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), List.of(value.backupFile()));
      case CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails value ->
          new CliEnvelopeFailurePaths(
              value.bookTarget(),
              value.bookTarget().equals(value.generatedSecretTarget())
                  ? List.of()
                  : List.of(value.generatedSecretTarget()));
      case CliMaintenanceRejectionJsonModels.BlockingArtifactsDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), value.blockingArtifacts());
      case CliArtifactPathFailureDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliMaintenanceRejectionJsonModels.ArtifactBusyDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliMaintenanceRejectionJsonModels.BackupAcknowledgementConflictDetails _ -> null;
      case CliMaintenanceRejectionJsonModels.ArtifactVerificationFailureDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliMaintenanceRejectionJsonModels.BackupFileDetails value ->
          new CliEnvelopeFailurePaths(value.backupFile(), List.of());
      case CliMaintenanceRejectionJsonModels.SecretTargetDetails value ->
          new CliEnvelopeFailurePaths(value.secretTarget(), List.of());
      case CliMaintenanceRejectionJsonModels.RecoveryPendingDetails value ->
          new CliEnvelopeFailurePaths(value.bookTarget(), List.of(value.generatedSecretTarget()));
    };
  }
}
