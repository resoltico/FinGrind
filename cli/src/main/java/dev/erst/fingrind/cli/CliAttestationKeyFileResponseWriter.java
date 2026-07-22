package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import java.util.List;
import java.util.Objects;

/** Renders public identity metadata for standalone encrypted attestation credentials. */
final class CliAttestationKeyFileResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAttestationKeyFileResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeGeneratedResult(AttestationKeyFileMetadata metadata, OutputMode outputMode) {
    writeMetadata(
        metadata,
        "Attestation Key File Generated",
        true,
        OperationId.GENERATE_ATTESTATION_KEY_FILE,
        outputMode);
  }

  void writeMetadata(AttestationKeyFileMetadata metadata, OutputMode outputMode) {
    writeMetadata(
        metadata,
        "Attestation Key File",
        false,
        OperationId.INSPECT_ATTESTATION_KEY_FILE,
        outputMode);
  }

  private void writeMetadata(
      AttestationKeyFileMetadata metadata,
      String title,
      boolean includesArtifact,
      OperationId operationId,
      OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliAdministrationJsonModels.AttestationKeyFilePayload(
                        metadata.credentialSpki(), metadata.keyId()),
                    includesArtifact
                        ? CliEnvelopeMapper.successArtifacts(
                            CliEnvelopeMapper.successArtifact(
                                ProtocolArtifactOutput.attestationKeyFileFormat(),
                                metadata.attestationKeyFilePath()))
                        : List.of())),
        () ->
            outputChannel.writeText(
                CliBookAccessOutputRenderer.renderAttestationKeyFileMetadata(title, metadata)),
        () -> {
          throw new IllegalArgumentException(CliOperationText.unsupportedCsvOutput(operationId));
        });
  }
}
