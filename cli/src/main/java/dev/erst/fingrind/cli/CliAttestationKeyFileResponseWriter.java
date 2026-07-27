package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookLifecycleJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.attestation.AttestationKeyFileCreation;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Renders public identity metadata for standalone encrypted attestation credentials. */
final class CliAttestationKeyFileResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAttestationKeyFileResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeGeneratedResult(AttestationKeyFileCreation createdKeyFile, OutputMode outputMode) {
    AttestationKeyFileCreation created = Objects.requireNonNull(createdKeyFile, "createdKeyFile");
    writeMetadata(
        new AttestationKeyFileMetadata(
            created.keyFilePath(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(created.credential().spki()),
            HexFormat.of().formatHex(created.credential().keyId())),
        "Attestation Key File Generated",
        created.publication(),
        OperationId.GENERATE_ATTESTATION_KEY_FILE,
        outputMode);
  }

  void writeMetadata(AttestationKeyFileMetadata metadata, OutputMode outputMode) {
    writeMetadata(
        metadata,
        "Attestation Key File",
        null,
        OperationId.INSPECT_ATTESTATION_KEY_FILE,
        outputMode);
  }

  private void writeMetadata(
      AttestationKeyFileMetadata metadata,
      String title,
      @Nullable ArtifactPublicationResult publication,
      OperationId operationId,
      OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliBookLifecycleJsonModels.AttestationKeyFilePayload(
                        metadata.credentialSpki(), metadata.keyId()),
                    publication == null
                        ? List.of()
                        : CliEnvelopeMapper.successArtifacts(
                            CliEnvelopeMapper.successArtifact(
                                ProtocolArtifactOutput.attestationKeyFileFormat(), publication)))),
        () ->
            outputChannel.writeText(
                CliBookAccessOutputRenderer.renderAttestationKeyFileMetadata(
                    title, metadata, publication)),
        () -> {
          throw new IllegalArgumentException(CliOperationText.unsupportedCsvOutput(operationId));
        });
  }
}
