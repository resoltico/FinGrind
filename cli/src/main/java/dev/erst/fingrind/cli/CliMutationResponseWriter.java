package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.attestation.AttestationKeyFileCreation;
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

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    administrativeWriter.writeOpenBookResult(bookFilePath, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    administrativeWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
  }

  void writeGeneratedAttestationKeyFileResult(
      AttestationKeyFileCreation createdKeyFile, OutputMode outputMode) {
    administrativeWriter.writeGeneratedAttestationKeyFileResult(createdKeyFile, outputMode);
  }

  void writeAttestationKeyFileMetadata(AttestationKeyFileMetadata metadata, OutputMode outputMode) {
    administrativeWriter.writeAttestationKeyFileMetadata(metadata, outputMode);
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    administrativeWriter.writeRekeyBookResult(result, outputMode);
  }

  void writeAttestationRegistryMutationResult(
      dev.erst.fingrind.contract.protocol.OperationId operationId,
      AttestationRegistryMutationResult result,
      OutputMode outputMode) {
    administrativeWriter.writeAttestationRegistryMutationResult(operationId, result, outputMode);
  }

  void writeBackupBookResult(
      dev.erst.fingrind.contract.bookkeeping.BackupBookResult result, OutputMode outputMode) {
    maintenanceWriter.writeBackupBookResult(result, outputMode);
  }

  void writeRestoreBookResult(
      dev.erst.fingrind.contract.bookkeeping.RestoreBookResult result, OutputMode outputMode) {
    maintenanceWriter.writeRestoreBookResult(result, outputMode);
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
