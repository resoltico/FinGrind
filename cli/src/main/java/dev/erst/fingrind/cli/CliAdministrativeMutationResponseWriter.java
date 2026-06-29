package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Renders administrative CLI mutation results through the shared output channel. */
final class CliAdministrativeMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAdministrativeMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    switch (result) {
      case OpenBookResult.Opened opened ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.OpenBookPayload(
                              absolutePath(bookFilePath),
                              opened.initializedAt().toString(),
                              CliBookInspectionPayloadMapper.bookIdentityPayload(
                                  opened.bookIdentity())))),
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
                    new CliAdministrationJsonModels.GeneratedBookKeyFilePayload(
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions()),
                    CliEnvelopeMapper.successArtifacts(
                        CliEnvelopeMapper.successArtifact(
                            ProtocolArtifactOutput.bookKeyFileFormat(),
                            generatedKeyFile.bookKeyFilePath())))),
        () ->
            outputChannel.writeText(
                CliBookAccessOutputRenderer.renderGeneratedBookKeyFileText(generatedKeyFile)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.GENERATE_BOOK_KEY_FILE));
        });
  }

  void writeRekeyBookResult(
      RekeyBookResult result,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    switch (result) {
      case RekeyBookResult.Rekeyed rekeyed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.RekeyBookPayload(
                              absolutePath(rekeyed.bookFilePath()),
                              replacementPassphraseSourceKind(replacementPassphraseSource)),
                          replacementPassphraseArtifacts(replacementPassphraseSource))),
              () ->
                  outputChannel.writeText(
                      CliBookAccessOutputRenderer.renderRekeyBookText(
                          rekeyed, replacementPassphraseSource)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.REKEY_BOOK));
              });
      case RekeyBookResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.REKEY_BOOK, rejected.rejection()),
              outputMode);
    }
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareAccountResult.Declared declared ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          declareAccountPayload("declared", declared.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderAccountDeclarationText(
                          "declared", declared.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Reactivated reactivated ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          declareAccountPayload("reactivated", reactivated.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderAccountDeclarationText(
                          "reactivated", reactivated.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Renamed renamed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          declareAccountPayload("renamed", renamed.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderAccountDeclarationText(
                          "renamed", renamed.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Unchanged unchanged ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          declareAccountPayload("unchanged", unchanged.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderAccountDeclarationText(
                          "unchanged", unchanged.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.DECLARE_ACCOUNT, rejected.rejection()),
              outputMode);
    }
  }

  private static CliDeclareAccountPayload declareAccountPayload(
      String outcome, dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account) {
    return new CliDeclareAccountPayload(outcome, CliBookQueryPayloadMapper.accountPayload(account));
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareTaxRegistrationResult.Declared declared ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliTaxPayloadMapper.taxRegistrationMutationPayload(
                              "declared", declared.registration()))),
              () ->
                  outputChannel.writeText(
                      CliTaxOutputRenderer.renderTaxRegistrationMutationText(
                          "declared", declared.registration())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_TAX_REGISTRATION));
              });
      case DeclareTaxRegistrationResult.Updated updated ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliTaxPayloadMapper.taxRegistrationMutationPayload(
                              "updated", updated.registration()))),
              () ->
                  outputChannel.writeText(
                      CliTaxOutputRenderer.renderTaxRegistrationMutationText(
                          "updated", updated.registration())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_TAX_REGISTRATION));
              });
      case DeclareTaxRegistrationResult.Unchanged unchanged ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliTaxPayloadMapper.taxRegistrationMutationPayload(
                              "unchanged", unchanged.registration()))),
              () ->
                  outputChannel.writeText(
                      CliTaxOutputRenderer.renderTaxRegistrationMutationText(
                          "unchanged", unchanged.registration())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_TAX_REGISTRATION));
              });
      case DeclareTaxRegistrationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxDeclarationRejectedEnvelope(rejected.rejection()),
              outputMode);
    }
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
                              closed.closedFiscalYear().closePostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList()))),
              () ->
                  outputChannel.writeText(
                      CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(
                          closed.closedFiscalYear())),
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

  private static String absolutePath(Path path) {
    return CliPublicPaths.redactedValue(path);
  }

  private static List<CliEnvelopeJsonModels.SuccessArtifact> replacementPassphraseArtifacts(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      return CliEnvelopeMapper.successArtifacts(
          CliEnvelopeMapper.successArtifact(
              ProtocolArtifactOutput.bookKeyFileFormat(), keyFile.bookKeyFilePath()));
    }
    return List.of();
  }

  private static String replacementPassphraseSourceKind(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    return switch (replacementPassphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "key-file";
      case BookAccess.PassphraseSource.StandardInput _ -> "standard-input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "interactive-prompt";
    };
  }
}
