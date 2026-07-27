package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.util.List;
import java.util.Objects;

/** Renders verification, review, and receipt read results through the shared output channel. */
final class CliAttestationReadResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAttestationReadResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeVerifyBook(
      VerifyBookAttestationResult result, boolean requireCleanAttestation, OutputMode outputMode) {
    switch (result) {
      case VerifyBookAttestationResult.Valid valid -> {
        if (requireCleanAttestation && valid.reviewRequired()) {
          writeReviewRequired(valid, outputMode);
          return;
        }
        outputMode.run(
            () ->
                outputChannel.writeEnvelope(
                    CliEnvelopeMapper.successEnvelope(
                        new CliAttestationJsonModels.VerifyBookPayload(
                            valid.bookId().toString(),
                            CliAttestationHeadPresentation.verifiedHeadPayload(
                                valid.headOrder(), valid.operationHeadHex()),
                            valid.previousHeadHex(),
                            valid.reviewRequired(),
                            CliAttestationReadPresentation.reviewFindingPayloads(
                                valid.reviewFindings()),
                            CliAttestationReadPresentation.registryPayload(valid.registry())))),
            () ->
                writeText(
                    valid.reviewRequired()
                        ? "Book Attestation Valid — Review Required"
                        : "Book Attestation Valid",
                    CliAttestationReadPresentation.verificationRows(
                        valid,
                        valid.reviewFindings().isEmpty()
                            ? "(none)"
                            : CliAttestationReviewTextRenderer.renderFindings(
                                valid.reviewFindings()))),
            () -> {
              throw new IllegalArgumentException(
                  CliOperationText.unsupportedCsvOutput(OperationId.VERIFY_BOOK));
            });
      }
      case VerifyBookAttestationResult.Invalid invalid ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationBookVerificationRejectedEnvelope(
                  failureFor(invalid.failureCode())),
              outputMode);
    }
  }

  private void writeReviewRequired(VerifyBookAttestationResult.Valid valid, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    null,
                    ContractErrors.Descriptor.ATTESTATION_REVIEW_REQUIRED.code(),
                    "The attestation chain is structurally valid, but declared compromise review"
                        + " findings prevent the required clean result.",
                    "Run "
                        + OperationId.ATTESTATION_REVIEW.wireName()
                        + " or "
                        + OperationId.VERIFY_BOOK.wireName()
                        + " without --require-clean-attestation to inspect the findings, then"
                        + " resolve the operational incident before accepting further work.",
                    null,
                    null,
                    new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
                        valid.bookId().toString(),
                        CliAttestationHeadPresentation.verifiedHeadPayload(
                            valid.headOrder(), valid.operationHeadHex()),
                        valid.previousHeadHex(),
                        CliAttestationReadPresentation.reviewFindingPayloads(
                            valid.reviewFindings())),
                    null,
                    null,
                    null,
                    null)),
        () ->
            writeText(
                "Book Attestation Review Required",
                CliAttestationReadPresentation.reviewRequiredRows(valid)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.VERIFY_BOOK));
        });
  }

  void writeReview(AttestationReviewResult result, OutputMode outputMode) {
    switch (result) {
      case AttestationReviewResult.Valid valid ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAttestationJsonModels.AttestationReviewPayload(
                              valid.bookId().toString(),
                              CliAttestationHeadPresentation.verifiedHeadPayload(
                                  valid.headOrder(), valid.operationHeadHex()),
                              CliAttestationReadPresentation.reviewFindingPayloads(
                                  valid.findings())))),
              () ->
                  writeText("Attestation Review", CliAttestationReadPresentation.reviewRows(valid)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.ATTESTATION_REVIEW));
              });
      case AttestationReviewResult.Invalid invalid ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationReviewVerificationRejectedEnvelope(
                  failureFor(invalid.failureCode())),
              outputMode);
    }
  }

  void writeExportReceipt(ExportAttestationReceiptResult result, OutputMode outputMode) {
    switch (result) {
      case ExportAttestationReceiptResult.Exported exported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAttestationJsonModels.ExportReceiptPayload(
                              CliPublicPaths.absoluteValue(exported.receiptFilePath()),
                              exported.bookId().toString(),
                              CliAttestationHeadPresentation.receiptAnchorPayload(
                                  exported.operationOrder(), exported.operationHeadHex()),
                              exported.warnings()),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  ProtocolArtifactOutput.attestationReceiptFormat(),
                                  exported.publication())))),
              () ->
                  writeText(
                      "Attestation Receipt Exported",
                      CliAttestationReadPresentation.receiptExportRows(exported)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.EXPORT_ATTESTATION_RECEIPT));
              });
      case ExportAttestationReceiptResult.AuthorizationRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationAuthorizationRejectedEnvelope(
                  rejected.failure()),
              outputMode);
      case ExportAttestationReceiptResult.VerificationRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationReceiptExportVerificationRejectedEnvelope(
                  rejected.failure()),
              outputMode);
    }
  }

  void writeVerifyReceipt(VerifyAttestationReceiptResult result, OutputMode outputMode) {
    switch (result) {
      case VerifyAttestationReceiptResult.Valid valid ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAttestationJsonModels.VerifyReceiptPayload(
                              CliPublicPaths.absoluteValue(valid.receiptFilePath()),
                              valid.bookId().toString(),
                              CliAttestationHeadPresentation.receiptAnchorPayload(
                                  valid.operationOrder(), valid.operationHeadHex()),
                              valid.findings()))),
              () ->
                  writeText(
                      "Attestation Receipt Valid",
                      CliAttestationReadPresentation.receiptVerificationRows(valid)),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.VERIFY_RECEIPT));
              });
      case VerifyAttestationReceiptResult.Invalid invalid ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationReceiptVerificationRejectedEnvelope(
                  failureFor(invalid.failureCode())),
              outputMode);
    }
  }

  private static AttestationVerificationFailure failureFor(String wireCode) {
    return AttestationVerificationFailure.fromWireCode(wireCode);
  }

  private void writeText(String title, List<List<String>> rows) {
    outputChannel.writeText(
        CliTextFormat.renderTitledBlock(title, CliTextFormat.renderKeyValueBlock(rows)));
  }
}
