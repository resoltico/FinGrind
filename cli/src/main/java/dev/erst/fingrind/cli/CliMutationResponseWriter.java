package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import java.nio.file.Path;
import java.util.Objects;

/** Renders write-side CLI results through the shared output channel. */
final class CliMutationResponseWriter {
  private final CliOutputChannel outputChannel;
  private final CliAdministrativeMutationResponseWriter administrativeWriter;
  private final CliMaintenanceMutationResponseWriter maintenanceWriter;

  CliMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
    this.administrativeWriter = new CliAdministrativeMutationResponseWriter(outputChannel);
    this.maintenanceWriter = new CliMaintenanceMutationResponseWriter(outputChannel);
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    switch (result) {
      case PostEntryResult.PreflightAccepted accepted ->
          outputMode.run(
              () -> outputChannel.writeEnvelope(CliEnvelopeMapper.preflightEnvelope(accepted)),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderPreflightAcceptedText(accepted)),
              () -> {
                throw new IllegalArgumentException("entry success does not support CSV output.");
              });
      case PostEntryResult.Committed committed ->
          outputMode.run(
              () -> outputChannel.writeEnvelope(CliEnvelopeMapper.committedEnvelope(committed)),
              () ->
                  outputChannel.writeText(CliMutationOutputRenderer.renderCommittedText(committed)),
              () -> {
                throw new IllegalArgumentException("entry success does not support CSV output.");
              });
      case PostEntryResult.PreflightRejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()));
      case PostEntryResult.CommitRejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()));
    }
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    administrativeWriter.writeOpenBookResult(bookFilePath, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    administrativeWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
  }

  void writeRekeyBookResult(
      RekeyBookResult result,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    administrativeWriter.writeRekeyBookResult(result, replacementPassphraseSource, outputMode);
  }

  void writeBackupBookResult(
      dev.erst.fingrind.contract.bookkeeping.BackupBookResult result, OutputMode outputMode) {
    maintenanceWriter.writeBackupBookResult(result, outputMode);
  }

  void writeRestoreBookResult(
      dev.erst.fingrind.contract.bookkeeping.RestoreBookResult result, OutputMode outputMode) {
    maintenanceWriter.writeRestoreBookResult(result, outputMode);
  }

  void writeInspectRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    maintenanceWriter.writeInspectRekeyRollbackResult(result, outputMode);
  }

  void writeRestoreRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    maintenanceWriter.writeRestoreRekeyRollbackResult(result, outputMode);
  }

  void writeDeleteRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    maintenanceWriter.writeDeleteRekeyRollbackResult(result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    administrativeWriter.writeDeclareAccountResult(result, outputMode);
  }

  void writePeriodResultTransferResult(PeriodResultTransferResult result, OutputMode outputMode) {
    administrativeWriter.writePeriodResultTransferResult(result, outputMode);
  }
}
