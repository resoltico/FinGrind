package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.SystemUtcClock;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Renders non-report read-side CLI results through the shared output channel. */
final class CliBookReadResponseWriter {
  private final CliOutputChannel outputChannel;
  private final Clock clock;
  private final CliAttestationReadResponseWriter attestationWriter;

  CliBookReadResponseWriter(CliOutputChannel outputChannel) {
    this(outputChannel, SystemUtcClock.instance());
  }

  CliBookReadResponseWriter(CliOutputChannel outputChannel, Clock clock) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.attestationWriter = new CliAttestationReadResponseWriter(this.outputChannel);
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

  void writeVerifyBookAttestation(VerifyBookAttestationResult result, OutputMode outputMode) {
    attestationWriter.writeVerifyBook(result, outputMode);
  }

  void writeAttestationReview(AttestationReviewResult result, OutputMode outputMode) {
    attestationWriter.writeReview(result, outputMode);
  }

  void writeExportAttestationReceipt(ExportAttestationReceiptResult result, OutputMode outputMode) {
    attestationWriter.writeExportReceipt(result, outputMode);
  }

  void writeVerifyAttestationReceipt(VerifyAttestationReceiptResult result, OutputMode outputMode) {
    attestationWriter.writeVerifyReceipt(result, outputMode);
  }

  void writeListAccountsResult(
      ListAccountsResult result, boolean withContext, OutputMode outputMode) {
    switch (result) {
      case ListAccountsResult.Listed listed ->
          writeAccountPage(listed, withContext, outputMode, Instant.now(clock));
      case ListAccountsResult.Rejected rejected ->
          writeQueryRejection(rejected.rejection(), outputMode);
    }
  }

  void writeListTaxRegistrationsResult(
      ListTaxRegistrationsResult result, boolean withContext, OutputMode outputMode) {
    switch (result) {
      case ListTaxRegistrationsResult.Listed listed ->
          writeTaxRegistrationPage(listed, withContext, outputMode, Instant.now(clock));
      case ListTaxRegistrationsResult.Rejected rejected ->
          writeTaxQueryRejection(rejected.rejection(), outputMode);
    }
  }

  void writeGetPostingResult(GetPostingResult result, boolean withContext, OutputMode outputMode) {
    switch (result) {
      case GetPostingResult.Found found ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingDetailsPayload(
                              found, Instant.now(clock)))),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingText(
                          found.bookIdentity(), found.postingFact(), withContext)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.GET_POSTING));
              });
      case GetPostingResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.queryRejectedEnvelope(rejected.rejection()), outputMode);
    }
  }

  void writeListPostingsResult(
      ListPostingsResult result, boolean withContext, OutputMode outputMode) {
    switch (result) {
      case ListPostingsResult.Listed listed ->
          writeListedResult(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          CliBookQueryPayloadMapper.postingPagePayload(
                              listed.query(), listed.page(), Instant.now(clock)))),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterText(
                          listed.page(), withContext)),
              () ->
                  outputChannel.writeText(
                      CliPostingOutputRenderer.renderPostingRegisterCsv(listed.page())),
              outputMode);
      case ListPostingsResult.Rejected rejected ->
          writeQueryRejection(rejected.rejection(), outputMode);
    }
  }

  private void writeListedResult(
      Runnable jsonWriter, Runnable textWriter, Runnable csvWriter, OutputMode outputMode) {
    outputMode.run(jsonWriter, textWriter, csvWriter);
  }

  private void writeAccountPage(
      ListAccountsResult.Listed listed,
      boolean withContext,
      OutputMode outputMode,
      Instant generatedAt) {
    AccountPage page = listed.page();
    writeListedResult(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliBookQueryPayloadMapper.accountPagePayload(
                        listed.query(), page, generatedAt))),
        () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderText(page, withContext)),
        () -> outputChannel.writeText(CliAccountPageOutputRenderer.renderCsv(page)),
        outputMode);
  }

  private void writeTaxRegistrationPage(
      ListTaxRegistrationsResult.Listed listed,
      boolean withContext,
      OutputMode outputMode,
      Instant generatedAt) {
    TaxRegistrationPage page = listed.page();
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    CliTaxPayloadMapper.taxRegistrationPagePayload(
                        listed.query(), page, generatedAt))),
        () ->
            outputChannel.writeText(
                CliTaxOutputRenderer.renderTaxRegistrationListText(page, withContext)),
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
