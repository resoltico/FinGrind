package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookLifecycleJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders CLI results for attestation registry mutations. */
final class CliAttestationRegistryMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAttestationRegistryMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeResult(
      OperationId operationId, AttestationRegistryMutationResult result, OutputMode outputMode) {
    switch (result) {
      case AttestationRegistryMutationResult.Mutated mutated ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookLifecycleJsonModels.AttestationRegistryMutationPayload(
                              CliPublicPaths.absoluteValue(mutated.bookFilePath()),
                              mutated.operationKind(),
                              CliAttestationCommitPresentation.requiredPayload(
                                  mutated.attestationCommit())))),
              () ->
                  outputChannel.writeText(
                      CliTextFormat.renderTitledBlock(
                          "Attestation Registry Updated",
                          CliTextFormat.renderKeyValueBlock(registryMutationRows(mutated)))),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(operationId));
              });
      case AttestationRegistryMutationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(rejected.rejection()),
              outputMode);
      case AttestationRegistryMutationResult.AuthorizationRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationRegistryMutationRejectedEnvelope(
                  rejected.failure()),
              outputMode);
    }
  }

  private static List<List<String>> registryMutationRows(
      AttestationRegistryMutationResult.Mutated mutated) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(mutated.bookFilePath())));
    rows.add(List.of("Operation kind", mutated.operationKind()));
    CliAttestationCommitPresentation.appendTextRows(
        rows, mutated.attestationCommit(), "No attestation operation was returned");
    return List.copyOf(rows);
  }
}
