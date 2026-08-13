package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookLifecycleJsonModels;
import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.nio.file.Path;
import java.util.Objects;

/** Renders CLI results for book lifecycle mutations. */
final class CliBookLifecycleMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliBookLifecycleMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    switch (result) {
      case OpenBookResult.Opened opened ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookLifecycleJsonModels.OpenBookPayload(
                              CliPublicPaths.absoluteValue(bookFilePath),
                              opened.initializedAt().toString(),
                              CliBookInspectionPayloadMapper.bookIdentityPayload(
                                  opened.bookIdentity()),
                              opened.attestationTrustRoot().bookId().toString(),
                              CliAttestationCommitPresentation.requiredPayload(
                                  opened.attestationCommit()),
                              CliAttestationPayloadMapper.registryPayload(
                                  opened.attestationTrustRoot())),
                          opened.publishedFounderKeyArtifacts().stream()
                              .map(
                                  publication ->
                                      CliEnvelopeMapper.successArtifact(
                                          ProtocolArtifactOutput.attestationKeyFileFormat(),
                                          publication))
                              .toList())),
              () ->
                  outputChannel.writeText(
                      CliBookAccessOutputRenderer.renderOpenBookText(bookFilePath, opened)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.OPEN_BOOK));
              });
      case OpenBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.OPEN_BOOK, rejected.rejection()),
              outputMode);
    }
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliBookLifecycleJsonModels.GeneratedBookKeyFilePayload(
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions()),
                    CliEnvelopeMapper.successArtifacts(
                        CliEnvelopeMapper.successArtifact(
                            ProtocolArtifactOutput.bookKeyFileFormat(),
                            generatedKeyFile.publication())))),
        () ->
            outputChannel.writeText(
                CliBookAccessOutputRenderer.renderGeneratedBookKeyFileText(generatedKeyFile)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.GENERATE_BOOK_KEY_FILE));
        });
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    switch (result) {
      case RekeyBookResult.Rekeyed rekeyed -> {
        PublicationTransactionArtifact generatedSecretPublication =
            rekeyed.pairPublication().generatedSecretPublication();
        outputMode.run(
            () ->
                outputChannel.writeEnvelope(
                    CliEnvelopeMapper.successEnvelope(
                        new CliBookPairPublicationJsonModels.RekeyBookPayload(
                            CliPublicPaths.absoluteValue(rekeyed.bookFilePath()),
                            CliPublicPaths.absoluteValue(rekeyed.newBookKeyFilePath()),
                            CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.from(
                                rekeyed.pairPublicationCompletion()),
                            CliProtectedBookPairPublicationPresentation.payload(
                                rekeyed.pairPublication()),
                            CliAttestationCommitPresentation.requiredPayload(
                                rekeyed.attestationCommit())),
                        CliEnvelopeMapper.successArtifacts(
                            CliEnvelopeMapper.successArtifact(
                                ProtocolArtifactOutput.bookKeyFileFormat(),
                                generatedSecretPublication)))),
            () -> outputChannel.writeText(CliBookAccessOutputRenderer.renderRekeyBookText(rekeyed)),
            () -> {
              throw new IllegalArgumentException(
                  CliOperationText.unsupportedCsvOutput(OperationId.REKEY_BOOK));
            });
      }
      case RekeyBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }
}
