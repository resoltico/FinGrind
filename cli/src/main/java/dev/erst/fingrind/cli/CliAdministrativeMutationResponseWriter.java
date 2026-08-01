package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.attestation.AttestationKeyFileCreation;
import java.nio.file.Path;
import java.util.Objects;

/** Routes administrative CLI mutation results to their response-specific writers. */
final class CliAdministrativeMutationResponseWriter {
  private final CliBookLifecycleMutationResponseWriter bookLifecycleWriter;
  private final CliAttestationKeyFileResponseWriter attestationKeyFileWriter;
  private final CliAttestationRegistryMutationResponseWriter attestationRegistryWriter;
  private final CliAccountRegistryMutationResponseWriter accountRegistryWriter;
  private final CliTaxRegistrationMutationResponseWriter taxRegistrationWriter;
  private final CliPeriodCloseMutationResponseWriter periodCloseWriter;

  CliAdministrativeMutationResponseWriter(CliOutputChannel outputChannel) {
    CliOutputChannel channel = Objects.requireNonNull(outputChannel, "outputChannel");
    this.bookLifecycleWriter = new CliBookLifecycleMutationResponseWriter(channel);
    this.attestationKeyFileWriter = new CliAttestationKeyFileResponseWriter(channel);
    this.attestationRegistryWriter = new CliAttestationRegistryMutationResponseWriter(channel);
    this.accountRegistryWriter = new CliAccountRegistryMutationResponseWriter(channel);
    this.taxRegistrationWriter = new CliTaxRegistrationMutationResponseWriter(channel);
    this.periodCloseWriter = new CliPeriodCloseMutationResponseWriter(channel);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    bookLifecycleWriter.writeOpenBookResult(bookFilePath, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    bookLifecycleWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
  }

  void writeGeneratedAttestationKeyFileResult(
      AttestationKeyFileCreation createdKeyFile, OutputMode outputMode) {
    attestationKeyFileWriter.writeGeneratedResult(createdKeyFile, outputMode);
  }

  void writeAttestationKeyFileMetadata(AttestationKeyFileMetadata metadata, OutputMode outputMode) {
    attestationKeyFileWriter.writeMetadata(metadata, outputMode);
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    bookLifecycleWriter.writeRekeyBookResult(result, outputMode);
  }

  void writeAttestationRegistryMutationResult(
      OperationId operationId, AttestationRegistryMutationResult result, OutputMode outputMode) {
    attestationRegistryWriter.writeResult(operationId, result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeDeclareAccountResult(result, outputMode);
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    taxRegistrationWriter.writeDeclareTaxRegistrationResult(result, outputMode);
  }

  void writeAmendAccountResult(AmendAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeAmendAccountResult(result, outputMode);
  }

  void writeRetireAccountResult(RetireAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeRetireAccountResult(result, outputMode);
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    periodCloseWriter.writeInterimResultSweepResult(result, outputMode);
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    periodCloseWriter.writeFiscalYearCloseResult(result, outputMode);
  }
}
