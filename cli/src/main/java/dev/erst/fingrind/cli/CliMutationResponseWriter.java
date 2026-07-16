package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import java.nio.file.Path;
import java.util.List;
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
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()),
              outputMode);
      case PostEntryResult.CommitRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()),
              outputMode);
    }
  }

  void writeOpenBookResult(
      Path bookFilePath,
      List<Path> tightenedParentDirectories,
      OpenBookResult result,
      OutputMode outputMode) {
    administrativeWriter.writeOpenBookResult(
        bookFilePath, tightenedParentDirectories, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile,
      List<Path> tightenedParentDirectories,
      OutputMode outputMode) {
    administrativeWriter.writeGenerateBookKeyFileResult(
        generatedKeyFile, tightenedParentDirectories, outputMode);
  }

  void writeRekeyBookResult(
      RekeyBookResult result, java.nio.file.Path newBookKeyFilePath, OutputMode outputMode) {
    administrativeWriter.writeRekeyBookResult(result, newBookKeyFilePath, outputMode);
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

  void writeAmendAccountResult(AmendAccountResult result, OutputMode outputMode) {
    administrativeWriter.writeAmendAccountResult(result, outputMode);
  }

  void writeRetireAccountResult(RetireAccountResult result, OutputMode outputMode) {
    administrativeWriter.writeRetireAccountResult(result, outputMode);
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    administrativeWriter.writeDeclareTaxRegistrationResult(result, outputMode);
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    administrativeWriter.writeInterimResultSweepResult(result, outputMode);
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    administrativeWriter.writeFiscalYearCloseResult(result, outputMode);
  }
}
