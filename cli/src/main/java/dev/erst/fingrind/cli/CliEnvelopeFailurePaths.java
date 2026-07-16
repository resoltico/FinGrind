package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliArtifactPathFailureDetails;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
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
    if (!(details instanceof CliRejectionJsonModels.MaintenanceRejectionDetails maintenance)) {
      return null;
    }
    return switch (maintenance) {
      case CliRejectionJsonModels.BookFileDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), List.of());
      case CliRejectionJsonModels.BookAndBackupFileDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), List.of(value.backupFile()));
      case CliRejectionJsonModels.BlockingArtifactsDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), value.blockingArtifacts());
      case CliArtifactPathFailureDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliRejectionJsonModels.ArtifactBusyDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliRejectionJsonModels.ArtifactVerificationFailureDetails value ->
          new CliEnvelopeFailurePaths(value.artifactPath(), List.of());
      case CliRejectionJsonModels.BackupFileDetails value ->
          new CliEnvelopeFailurePaths(value.backupFile(), List.of());
      case CliRejectionJsonModels.SecretTargetDetails value ->
          new CliEnvelopeFailurePaths(value.secretTarget(), List.of());
      case CliRejectionJsonModels.RollbackArtifactDetails value ->
          new CliEnvelopeFailurePaths(value.rollbackArtifact(), List.of());
      case CliRejectionJsonModels.RollbackArtifactMismatchDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), List.of(value.rollbackArtifact()));
      case CliRejectionJsonModels.RollbackArtifactSelectionDetails value ->
          new CliEnvelopeFailurePaths(value.bookFile(), value.rollbackArtifacts());
    };
  }
}
