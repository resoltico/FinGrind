package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
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
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Renders administrative CLI mutation results through the shared output channel. */
final class CliAdministrativeMutationResponseWriter {
  private final CliOutputChannel outputChannel;
  private final CliAccountRegistryMutationResponseWriter accountRegistryWriter;

  CliAdministrativeMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
    this.accountRegistryWriter = new CliAccountRegistryMutationResponseWriter(outputChannel);
  }

  void writeOpenBookResult(
      Path bookFilePath,
      List<Path> tightenedParentDirectories,
      OpenBookResult result,
      OutputMode outputMode) {
    switch (result) {
      case OpenBookResult.Opened opened ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.OpenBookPayload(
                              CliPublicPaths.absoluteValue(bookFilePath),
                              opened.initializedAt().toString(),
                              CliAdministrativeMutationPayloadSupport
                                  .tightenedParentDirectoryPayloads(tightenedParentDirectories),
                              CliBookInspectionPayloadMapper.bookIdentityPayload(
                                  opened.bookIdentity())))),
              () ->
                  outputChannel.writeText(
                      CliBookAccessOutputRenderer.renderOpenBookText(
                          bookFilePath, tightenedParentDirectories, opened)),
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
      GeneratedBookKeyFile generatedKeyFile,
      List<Path> tightenedParentDirectories,
      OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliAdministrationJsonModels.GeneratedBookKeyFilePayload(
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions(),
                        CliAdministrativeMutationPayloadSupport.tightenedParentDirectoryPayloads(
                            tightenedParentDirectories)),
                    CliEnvelopeMapper.successArtifacts(
                        CliEnvelopeMapper.successArtifact(
                            dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput
                                .bookKeyFileFormat(),
                            generatedKeyFile.bookKeyFilePath())))),
        () ->
            outputChannel.writeText(
                CliBookAccessOutputRenderer.renderGeneratedBookKeyFileText(
                    generatedKeyFile, tightenedParentDirectories)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.GENERATE_BOOK_KEY_FILE));
        });
  }

  void writeRekeyBookResult(
      RekeyBookResult result, java.nio.file.Path newBookKeyFilePath, OutputMode outputMode) {
    switch (result) {
      case RekeyBookResult.Rekeyed rekeyed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.RekeyBookPayload(
                              CliPublicPaths.absoluteValue(rekeyed.bookFilePath()),
                              CliPublicPaths.absoluteValue(newBookKeyFilePath)),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.bookKeyFileFormat(),
                                  newBookKeyFilePath)))),
              () ->
                  outputChannel.writeText(
                      CliBookAccessOutputRenderer.renderRekeyBookText(rekeyed, newBookKeyFilePath)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.REKEY_BOOK));
              });
      case RekeyBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliMaintenanceRejectionPayloadMapper.rejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }

  void writeAttestationRegistryMutationResult(
      OperationId operationId, AttestationRegistryMutationResult result, OutputMode outputMode) {
    switch (result) {
      case AttestationRegistryMutationResult.Mutated mutated ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.AttestationRegistryMutationPayload(
                              CliPublicPaths.absoluteValue(mutated.bookFilePath()),
                              mutated.operationKind(),
                              mutated.headOrder().toString()))),
              () ->
                  outputChannel.writeText(
                      "%s appended at attestation order %s for %s.%n"
                          .formatted(
                              operationId.wireName(),
                              mutated.headOrder(),
                              CliPublicPaths.absoluteValue(mutated.bookFilePath()))),
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
              CliRejectionPayloadMapper.attestationAuthorizationRejectedEnvelope(
                  rejected.failure()),
              outputMode);
    }
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeDeclareAccountResult(result, outputMode);
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareTaxRegistrationResult.Declared declared ->
          writeMutationSuccess(
              OperationId.DECLARE_TAX_REGISTRATION,
              "declared",
              declared.registration(),
              registration ->
                  CliTaxPayloadMapper.taxRegistrationMutationPayload("declared", registration),
              CliTaxOutputRenderer::renderTaxRegistrationMutationText,
              outputMode);
      case DeclareTaxRegistrationResult.Updated updated ->
          writeMutationSuccess(
              OperationId.DECLARE_TAX_REGISTRATION,
              "updated",
              updated.registration(),
              registration ->
                  CliTaxPayloadMapper.taxRegistrationMutationPayload("updated", registration),
              CliTaxOutputRenderer::renderTaxRegistrationMutationText,
              outputMode);
      case DeclareTaxRegistrationResult.Unchanged unchanged ->
          writeMutationSuccess(
              OperationId.DECLARE_TAX_REGISTRATION,
              "unchanged",
              unchanged.registration(),
              registration ->
                  CliTaxPayloadMapper.taxRegistrationMutationPayload("unchanged", registration),
              CliTaxOutputRenderer::renderTaxRegistrationMutationText,
              outputMode);
      case DeclareTaxRegistrationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxDeclarationRejectedEnvelope(rejected.rejection()),
              outputMode);
    }
  }

  void writeAmendAccountResult(AmendAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeAmendAccountResult(result, outputMode);
  }

  void writeRetireAccountResult(RetireAccountResult result, OutputMode outputMode) {
    accountRegistryWriter.writeRetireAccountResult(result, outputMode);
  }

  private <T> void writeMutationSuccess(
      OperationId operationId,
      String outcome,
      T subject,
      Function<T, dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload> payloadFactory,
      BiFunction<String, T, String> textRenderer,
      OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(payloadFactory.apply(subject))),
        () -> outputChannel.writeText(textRenderer.apply(outcome, subject)),
        () -> {
          throw new IllegalArgumentException(CliOperationText.unsupportedCsvOutput(operationId));
        });
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    switch (result) {
      case InterimResultSweepResult.Swept swept ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.SweptInterimResultPayload(
                              swept.sweptInterimResult().sweepOrder(),
                              swept
                                  .sweptInterimResult()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              swept
                                  .sweptInterimResult()
                                  .reportingPeriod()
                                  .effectiveDateTo()
                                  .toString(),
                              swept.sweptInterimResult().resultHoldingAccountCode().value(),
                              swept.sweptInterimResult().sweptTotals().stream()
                                  .map(CliPayloadAssembler::balancePayload)
                                  .toList(),
                              swept.sweptInterimResult().sweptAt().toString(),
                              swept.sweptInterimResult().sweepPostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList()))),
              () ->
                  outputChannel.writeText(
                      CliPeriodCloseOutputRenderer.renderSweptInterimResultText(
                          swept.sweptInterimResult())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.INTERIM_RESULT_SWEEP));
              });
      case InterimResultSweepResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.INTERIM_RESULT_SWEEP, rejected.rejection()),
              outputMode);
    }
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    switch (result) {
      case FiscalYearCloseResult.Closed closed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.ClosedFiscalYearPayload(
                              closed.closedFiscalYear().closeOrder(),
                              closed
                                  .closedFiscalYear()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              closed
                                  .closedFiscalYear()
                                  .reportingPeriod()
                                  .effectiveDateTo()
                                  .toString(),
                              closed.closedFiscalYear().capitalAccountCode().value(),
                              closed.closedFiscalYear().resultHoldingAccountCode().value(),
                              closed.closedFiscalYear().retainedAccumulatedAccountCode().value(),
                              closed.closedFiscalYear().closedAt().toString(),
                              closed.idempotentReplay(),
                              closed.closedFiscalYear().closePostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList()))),
              () ->
                  outputChannel.writeText(
                      CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(
                          closed.closedFiscalYear(), closed.idempotentReplay())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.FISCAL_YEAR_CLOSE));
              });
      case FiscalYearCloseResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.FISCAL_YEAR_CLOSE, rejected.rejection()),
              outputMode);
    }
  }
}
