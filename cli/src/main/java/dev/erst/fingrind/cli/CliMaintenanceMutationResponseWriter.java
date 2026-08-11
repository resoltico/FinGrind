package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Renders maintenance CLI mutation results through the shared output channel. */
final class CliMaintenanceMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliMaintenanceMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeBackupBookResult(BackupBookResult result, OutputMode outputMode) {
    switch (result) {
      case BackupBookResult.BackedUp backedUp ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookPairPublicationJsonModels.BackupBookPayload(
                              absolutePath(backedUp.bookFilePath()),
                              backedUp.backupId().toString(),
                              CliBookPairPublicationJsonModels.PairPublicationCompletionPayload
                                  .from(backedUp.pairPublicationCompletion()),
                              CliProtectedBookPairPublicationPresentation.payload(
                                  backedUp.pairPublication()),
                              CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload
                                  .from(backedUp.acknowledgementState()),
                              CliAttestationCommitPresentation.payload(
                                  backedUp.attestationCommit())),
                          pairSuccessArtifacts(
                              backedUp.pairPublication(),
                              ProtocolArtifactOutput.backupFileFormat(),
                              ProtocolArtifactOutput.backupKeyFileFormat()))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderBackupBookText(backedUp)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.BACKUP_BOOK));
              });
      case BackupBookResult.AcknowledgementPending pending ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookPairPublicationJsonModels.BackupBookPayload(
                              absolutePath(pending.bookFilePath()),
                              pending.backupId().toString(),
                              CliBookPairPublicationJsonModels.PairPublicationCompletionPayload
                                  .from(pending.pairPublicationCompletion()),
                              CliProtectedBookPairPublicationPresentation.payload(
                                  pending.pairPublication()),
                              CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload
                                  .PENDING,
                              null),
                          pairSuccessArtifacts(
                              pending.pairPublication(),
                              ProtocolArtifactOutput.backupFileFormat(),
                              ProtocolArtifactOutput.backupKeyFileFormat()))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderBackupAcknowledgementPendingText(
                          pending)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.BACKUP_BOOK));
              });
      case BackupBookResult.AcknowledgementAuthorizationRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.backupAcknowledgementAuthorizationRejectedEnvelope(
                  rejected),
              outputMode);
      case BackupBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }

  void writeRestoreBookResult(RestoreBookResult result, OutputMode outputMode) {
    switch (result) {
      case RestoreBookResult.Restored restored ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookPairPublicationJsonModels.RestoreBookPayload(
                              absolutePath(restored.bookFilePath()),
                              absolutePath(restored.bookKeyFilePath()),
                              CliBookPairPublicationJsonModels.PairPublicationCompletionPayload
                                  .from(restored.pairPublicationCompletion()),
                              CliProtectedBookPairPublicationPresentation.payload(
                                  restored.pairPublication()),
                              CliAttestationCommitPresentation.requiredPayload(
                                  restored.attestationCommit())),
                          pairSuccessArtifacts(
                              restored.pairPublication(),
                              ProtocolArtifactOutput.bookFileFormat(),
                              ProtocolArtifactOutput.bookKeyFileFormat()))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderRestoreBookText(restored)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.RESTORE_BOOK));
              });
      case RestoreBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }

  private static String absolutePath(Path path) {
    return CliPublicPaths.absoluteValue(path);
  }

  private static List<CliEnvelopeJsonModels.SuccessArtifact> pairSuccessArtifacts(
      @Nullable ProtectedBookPairPublication pairPublication,
      String bookFormat,
      String generatedSecretFormat) {
    if (pairPublication == null) {
      return List.of();
    }
    return CliEnvelopeMapper.successArtifacts(
        CliEnvelopeMapper.successArtifact(bookFormat, pairPublication.bookPublication()),
        CliEnvelopeMapper.successArtifact(
            generatedSecretFormat, pairPublication.generatedSecretPublication()));
  }
}
