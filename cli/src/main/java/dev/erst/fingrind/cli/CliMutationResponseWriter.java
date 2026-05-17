package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAdministrationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Renders write-side CLI results through the shared output channel. */
final class CliMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    switch (result) {
      case PostEntryResult.PreflightAccepted accepted ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(CliResponsePayloadMapper.preflightEnvelope(accepted)),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderPreflightAcceptedHuman(accepted)),
              () -> {
                throw new IllegalArgumentException("entry success does not support CSV output.");
              });
      case PostEntryResult.Committed committed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.committedEnvelope(committed)),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderCommittedHuman(committed)),
              () -> {
                throw new IllegalArgumentException("entry success does not support CSV output.");
              });
      case PostEntryResult.PreflightRejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()),
              rejected.requestIdempotencyKey().value());
      case PostEntryResult.CommitRejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.postingRejectedEnvelope(
                  rejected.requestIdempotencyKey().value(), rejected.rejection()),
              rejected.requestIdempotencyKey().value());
    }
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    switch (result) {
      case OpenBookResult.Opened opened ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          new CliAdministrationJsonModels.OpenBookPayload(
                              absolutePath(bookFilePath),
                              opened.initializedAt().toString(),
                              CliBookPayloadMapper.bookIdentityPayload(opened.bookIdentity())))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderOpenBookHuman(bookFilePath, opened)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.OPEN_BOOK));
              });
      case OpenBookResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()),
              null);
    }
  }

  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliResponsePayloadMapper.successEnvelope(
                    new CliAdministrationJsonModels.GeneratedBookKeyFilePayload(
                        absolutePath(generatedKeyFile.bookKeyFilePath()),
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions()))),
        () ->
            outputChannel.writeText(
                CliMutationOutputRenderer.renderGeneratedBookKeyFileHuman(generatedKeyFile)),
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
                      CliResponsePayloadMapper.successEnvelope(
                          new CliAdministrationJsonModels.RekeyBookPayload(
                              absolutePath(rekeyed.bookFilePath()),
                              replacementPassphraseSourceKind(replacementPassphraseSource),
                              replacementBookKeyFile(replacementPassphraseSource)))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderRekeyBookHuman(
                          rekeyed, replacementPassphraseSource)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.REKEY_BOOK));
              });
      case RekeyBookResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()),
              null);
    }
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    switch (result) {
      case DeclareAccountResult.Declared declared ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountPayload(declared.account()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderDeclaredAccountHuman(declared.account())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.DECLARE_ACCOUNT));
              });
      case DeclareAccountResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()),
              null);
    }
  }

  void writeClosePeriodResult(ClosePeriodResult result, OutputMode outputMode) {
    switch (result) {
      case ClosePeriodResult.Closed closed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          new CliAdministrationJsonModels.ClosedPeriodPayload(
                              closed.closedPeriod().closeOrder(),
                              closed
                                  .closedPeriod()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              closed.closedPeriod().reportingPeriod().effectiveDateTo().toString(),
                              closed.closedPeriod().closingEquityAccountCode().value(),
                              closed.closedPeriod().closedTotals().stream()
                                  .map(CliPayloadAssembler::balancePayload)
                                  .toList(),
                              closed.closedPeriod().closedAt().toString(),
                              closed.closedPeriod().closingPostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList()))),
              () ->
                  outputChannel.writeText(
                      CliMutationOutputRenderer.renderClosedPeriodHuman(closed.closedPeriod())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.CLOSE_PERIOD));
              });
      case ClosePeriodResult.Rejected rejected ->
          outputChannel.writeMutationRejection(
              outputMode,
              CliResponsePayloadMapper.administrationRejectedEnvelope(rejected.rejection()),
              null);
    }
  }

  private static String absolutePath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }

  private static String replacementPassphraseSourceKind(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    return switch (replacementPassphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> "key-file";
      case BookAccess.PassphraseSource.StandardInput _ -> "standard-input";
      case BookAccess.PassphraseSource.InteractivePrompt _ -> "interactive-prompt";
    };
  }

  private static @Nullable String replacementBookKeyFile(
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (replacementPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile) {
      return absolutePath(keyFile.bookKeyFilePath());
    }
    return null;
  }
}
