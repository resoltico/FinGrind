package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
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
      case ListAccountsResult.Listed listed -> writeAccountPage(listed.page(), outputMode);
      case ListAccountsResult.Rejected rejected ->
          writeQueryRejection(rejected.rejection(), outputMode);
    }
  }

  void writeListTaxRegistrationsResult(ListTaxRegistrationsResult result, OutputMode outputMode) {
    switch (result) {
      case ListTaxRegistrationsResult.Listed listed ->
          writeTaxRegistrationPage(listed.page(), outputMode);
      case ListTaxRegistrationsResult.Rejected rejected ->
          writeTaxQueryRejection(rejected.rejection(), outputMode);
    }
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    switch (result) {
      case GetPostingResult.Found found ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingDetailsPayload(found))),
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
          writeListedResult(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingPagePayload(listed.page()))),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterText(listed.page())),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterCsv(listed.page())),
              outputMode);
      case ListPostingsResult.Rejected rejected ->
          writeQueryRejection(rejected.rejection(), outputMode);
    }
  }

  void writeTaxObligationResult(TaxObligationResult result, OutputMode outputMode) {
    switch (result) {
      case TaxObligationResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliTaxPayloadMapper.taxObligationPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliTaxOutputRenderer.renderTaxObligationText(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliTaxOutputRenderer.renderTaxObligationCsv(reported.report())));
      case TaxObligationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxQueryRejectedEnvelope(
                  OperationId.TAX_OBLIGATION, rejected.rejection()),
              outputMode);
    }
  }

  private void writeListedResult(
      Runnable jsonWriter, Runnable textWriter, Runnable csvWriter, OutputMode outputMode) {
    outputMode.run(jsonWriter, textWriter, csvWriter);
  }

  private void writeAccountPage(AccountPage page, OutputMode outputMode) {
    writeListedResult(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliBookQueryPayloadMapper.accountPagePayload(page))),
        () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderText(page)),
        () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderCsv(page)),
        outputMode);
  }

  private void writeTaxRegistrationPage(TaxRegistrationPage page, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliTaxPayloadMapper.taxRegistrationPagePayload(page))),
        () -> outputChannel.writeText(CliTaxOutputRenderer.renderTaxRegistrationListText(page)),
        () -> outputChannel.writeText(CliTaxOutputRenderer.renderTaxRegistrationListCsv(page)));
  }

  private void writeQueryRejection(BookQueryRejection rejection, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.queryRejectedEnvelope(rejection), outputMode);
  }

  private void writeTaxQueryRejection(TaxQueryRejection rejection, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.taxQueryRejectedEnvelope(
            OperationId.LIST_TAX_REGISTRATIONS, rejection),
        outputMode);
  }
}
