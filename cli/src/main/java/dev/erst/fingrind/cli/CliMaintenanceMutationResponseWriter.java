package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import java.nio.file.Path;
import java.util.Objects;

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
                          new CliAdministrationJsonModels.BackupBookPayload(
                              absolutePath(backedUp.bookFilePath()),
                              backedUp.backupId().toString(),
                              backedUp.acknowledgementResumed() ? "resumed" : "acknowledged"),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupFileFormat(),
                                  backedUp.backupFilePath()),
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupKeyFileFormat(),
                                  backedUp.backupBookKeyFilePath())))),
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
                          new CliAdministrationJsonModels.BackupBookPayload(
                              absolutePath(pending.bookFilePath()),
                              pending.backupId().toString(),
                              "pending"),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupFileFormat(),
                                  pending.backupFilePath()),
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupKeyFileFormat(),
                                  pending.backupBookKeyFilePath())))),
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
                  rejected.failure()),
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
                          new CliAdministrationJsonModels.RestoreBookPayload(
                              absolutePath(restored.bookFilePath()),
                              absolutePath(restored.bookKeyFilePath())),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.bookFileFormat(), restored.bookFilePath()),
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.bookKeyFileFormat(),
                                  restored.bookKeyFilePath())))),
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
}
