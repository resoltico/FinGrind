package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.List;
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
                              absolutePath(backedUp.bookFilePath())),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupBookFileFormat(),
                                  backedUp.backupFilePath()),
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupBookKeyFileFormat(),
                                  backedUp.backupBookKeyFilePath())))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderBackupBookText(backedUp)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.BACKUP_BOOK));
              });
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
                              absolutePath(restored.bookFilePath())),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupBookFileFormat(),
                                  restored.backupFilePath()),
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.backupBookKeyFileFormat(),
                                  restored.backupBookKeyFilePath())))),
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

  void writeInspectRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    switch (result) {
      case RekeyRollbackResult.Inspected inspected ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.InspectRekeyRollbackPayload(
                              absolutePath(inspected.bookFilePath())),
                          rollbackArtifacts(inspected.rollbackArtifactPaths()))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderInspectRekeyRollbackText(inspected)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.INSPECT_REKEY_ROLLBACK));
              });
      case RekeyRollbackResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejected.rejection()),
              outputMode);
      default ->
          throw new IllegalArgumentException(
              "Inspect rekey rollback received unexpected result type: "
                  + result.getClass().getSimpleName());
    }
  }

  void writeRestoreRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    switch (result) {
      case RekeyRollbackResult.Restored restored ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.RestoreRekeyRollbackPayload(
                              absolutePath(restored.bookFilePath())),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.rollbackBookFileFormat(),
                                  restored.rollbackArtifactPath())))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderRestoreRekeyRollbackText(restored)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.RESTORE_REKEY_ROLLBACK));
              });
      case RekeyRollbackResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejected.rejection()),
              outputMode);
      default ->
          throw new IllegalArgumentException(
              "Restore rekey rollback received unexpected result type: "
                  + result.getClass().getSimpleName());
    }
  }

  void writeDeleteRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    switch (result) {
      case RekeyRollbackResult.Deleted deleted ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.DeleteRekeyRollbackPayload(
                              absolutePath(deleted.bookFilePath())),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.rollbackBookFileFormat(),
                                  deleted.rollbackArtifactPath())))),
              () ->
                  outputChannel.writeText(
                      CliBookMaintenanceOutputRenderer.renderDeleteRekeyRollbackText(deleted)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DELETE_REKEY_ROLLBACK));
              });
      case RekeyRollbackResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.maintenanceRejectedEnvelope(rejected.rejection()),
              outputMode);
      default ->
          throw new IllegalArgumentException(
              "Delete rekey rollback received unexpected result type: "
                  + result.getClass().getSimpleName());
    }
  }

  private static String absolutePath(PublicPathHint pathHint) {
    return CliPublicPaths.redactedValue(pathHint);
  }

  private static List<CliEnvelopeJsonModels.SuccessArtifact> rollbackArtifacts(
      List<PublicPathHint> rollbackArtifactPaths) {
    if (rollbackArtifactPaths.isEmpty()) {
      return List.of();
    }
    return rollbackArtifactPaths.stream()
        .map(
            rollbackArtifactPath ->
                CliEnvelopeMapper.successArtifact(
                    ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath))
        .toList();
  }
}
