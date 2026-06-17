package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodResultTransferResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
                      CliMutationOutputRenderer.renderOpenBookText(bookFilePath, opened)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.OPEN_BOOK));
              });
      case OpenBookResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliAdministrationJsonModels.GeneratedBookKeyFilePayload(
                        absolutePath(generatedKeyFile.bookKeyFilePath()),
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions()))),
        () ->
            outputChannel.writeText(
                CliMutationOutputRenderer.renderGeneratedBookKeyFileText(generatedKeyFile)),
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
                              replacementPassphraseSourceKind(replacementPassphraseSource),
                              replacementBookKeyFile(replacementPassphraseSource)))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderRekeyBookText(
                          rekeyed, replacementPassphraseSource)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.REKEY_BOOK));
              });
      case RekeyBookResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareAccountResult.Declared declared ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.accountPayload(declared.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderDeclaredAccountText(declared.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
    }
  }

  void writePeriodResultTransferResult(PeriodResultTransferResult result, OutputMode outputMode) {
    switch (result) {
      case PeriodResultTransferResult.Transferred closed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAdministrationJsonModels.TransferredPeriodResultPayload(
                              closed.transferredPeriodResult().transferOrder(),
                              closed
                                  .transferredPeriodResult()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              closed
                                  .transferredPeriodResult()
                                  .reportingPeriod()
                                  .effectiveDateTo()
                                  .toString(),
                              closed.transferredPeriodResult().resultHoldingAccountCode().value(),
                              closed.transferredPeriodResult().transferredTotals().stream()
                                  .map(CliPayloadAssembler::balancePayload)
                                  .toList(),
                              closed.transferredPeriodResult().transferredAt().toString(),
                              closed.transferredPeriodResult().transferPostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderTransferredPeriodResultText(
                          closed.transferredPeriodResult())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.TRANSFER_PERIOD_RESULT));
              });
      case PeriodResultTransferResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(rejected.rejection()));
    }
  }

  private static String absolutePath(Path path) {
    return CliPublicPaths.redactedValue(path);
  }

  private static @Nullable String replacementBookKeyFile(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      return absolutePath(keyFile.bookKeyFilePath());
    }
    return null;
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
