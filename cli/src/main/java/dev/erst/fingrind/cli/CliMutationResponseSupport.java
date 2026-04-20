package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;

/** Renders write-side CLI results through the shared output channel. */
final class CliMutationResponseSupport {
  private final CliOutputChannel outputChannel;

  CliMutationResponseSupport(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    switch (result) {
      case PostEntryResult.PreflightAccepted accepted ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.preflightEnvelope(accepted), false),
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
                      CliResponsePayloadMapper.committedEnvelope(committed), false),
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
                          new CliResponseJsonModels.OpenBookPayload(
                              absolutePath(bookFilePath), opened.initializedAt().toString())),
                      false),
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
                    new CliResponseJsonModels.GeneratedBookKeyFilePayload(
                        absolutePath(generatedKeyFile.bookKeyFilePath()),
                        generatedKeyFile.encoding(),
                        generatedKeyFile.entropyBits(),
                        generatedKeyFile.permissions())),
                false),
        () ->
            outputChannel.writeText(
                CliMutationOutputRenderer.renderGeneratedBookKeyFileHuman(generatedKeyFile)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.GENERATE_BOOK_KEY_FILE));
        });
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    switch (result) {
      case RekeyBookResult.Rekeyed rekeyed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          new CliResponseJsonModels.RekeyBookPayload(
                              absolutePath(rekeyed.bookFilePath()))),
                      false),
              () ->
                  outputChannel.writeText(CliMutationOutputRenderer.renderRekeyBookHuman(rekeyed)),
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
                          CliResponsePayloadMapper.accountPayload(declared.account())),
                      false),
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

  private static String absolutePath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }
}
