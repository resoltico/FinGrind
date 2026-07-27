package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import java.util.Objects;

/** Renders CLI results for tax-registration mutations. */
final class CliTaxRegistrationMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliTaxRegistrationMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareTaxRegistrationResult.Declared declared ->
          writeMutationSuccess(
              CliTaxJsonModels.TaxRegistrationMutationOutcome.DECLARED,
              declared.registration(),
              declared.attestationCommit(),
              outputMode);
      case DeclareTaxRegistrationResult.Updated updated ->
          writeMutationSuccess(
              CliTaxJsonModels.TaxRegistrationMutationOutcome.UPDATED,
              updated.registration(),
              updated.attestationCommit(),
              outputMode);
      case DeclareTaxRegistrationResult.Unchanged unchanged ->
          writeMutationSuccess(
              CliTaxJsonModels.TaxRegistrationMutationOutcome.UNCHANGED,
              unchanged.registration(),
              unchanged.attestationCommit(),
              outputMode);
      case DeclareTaxRegistrationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxDeclarationRejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }

  private void writeMutationSuccess(
      CliTaxJsonModels.TaxRegistrationMutationOutcome outcome,
      DeclaredTaxRegistration registration,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit,
      OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliTaxPayloadMapper.taxRegistrationMutationPayload(
                        outcome, registration, attestationCommit))),
        () ->
            outputChannel.writeText(
                CliTaxOutputRenderer.renderTaxRegistrationMutationText(
                    outcome, registration, attestationCommit)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_TAX_REGISTRATION));
        });
  }
}
