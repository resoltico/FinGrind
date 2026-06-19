package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import java.nio.file.Path;
import java.util.Objects;

/** Renders non-report read-side CLI results through the shared output channel. */
final class CliBookReadResponseWriter {
  private final CliOutputChannel outputChannel;

  CliBookReadResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliBookInspectionPayloadMapper.bookInspectionPayload(
                        bookFilePath, inspection))),
        () ->
            outputChannel.writeText(
                CliBookInspectionOutputRenderer.renderText(bookFilePath, inspection)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.INSPECT_BOOK));
        });
  }

  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    switch (result) {
      case ListAccountsResult.Listed listed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.accountPagePayload(listed.page()))),
              () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderText(listed.page())),
              () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderCsv(listed.page())));
      case ListAccountsResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.queryRejectedEnvelope(rejected.rejection()), outputMode);
    }
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    switch (result) {
      case GetPostingResult.Found found ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingDetailsPayload(
                              found.bookIdentity(), found.postingFact()))),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingText(
                          found.bookIdentity(), found.postingFact())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.GET_POSTING));
              });
      case GetPostingResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.queryRejectedEnvelope(rejected.rejection()), outputMode);
    }
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    switch (result) {
      case ListPostingsResult.Listed listed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingPagePayload(listed.page()))),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterText(listed.page())),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterCsv(listed.page())));
      case ListPostingsResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.queryRejectedEnvelope(rejected.rejection()), outputMode);
    }
  }
}
