package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.util.List;
import java.util.Objects;

/** Renders verification, review, and receipt read results through the shared output channel. */
final class CliAttestationReadResponseWriter {
  private final CliOutputChannel outputChannel;

  CliAttestationReadResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeVerifyBook(VerifyBookAttestationResult result, OutputMode outputMode) {
    switch (result) {
      case VerifyBookAttestationResult.Valid valid ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliAttestationJsonModels.VerifyBookPayload(
                              valid.bookId().toString(),
                              valid.headOrder().toString(),
                              valid.operationHeadHex(),
                              valid.reviewRequired(),
                              reviewFindingPayloads(valid.reviewFindings())))),
              () ->
                  writeText(
                      valid.reviewRequired()
                          ? "Book Attestation Valid — Review Required"
                          : "Book Attestation Valid",
                      List.of(
                          List.of("Book ID", valid.bookId().toString()),
                          List.of("Head order", valid.headOrder().toString()),
                          List.of("Operation head", valid.operationHeadHex()),
                          List.of(
                              "Review findings",
                              valid.reviewFindings().isEmpty()
                                  ? "(none)"
                                  : renderedReviewFindings(valid.reviewFindings())))),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.VERIFY_BOOK));
              });
      case VerifyBookAttestationResult.Invalid invalid ->
          writeRejected(
              invalid.failureCode(),
              "The selected book's attestation chain is structurally invalid.",
              "Restore from a valid independently retained backup or use a verified receipt to investigate the break.",
              outputMode);
    }
  }

  void writeReview(AttestationReviewResult result, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(
                    new CliAttestationJsonModels.AttestationReviewPayload(
                        result.bookId().toString(),
                        result.headOrder().toString(),
                        reviewFindingPayloads(result.findings())))),
        () ->
            writeText(
                "Attestation Review",
                List.of(
                    List.of("Book ID", result.bookId().toString()),
                    List.of("Head order", result.headOrder().toString()),
                    List.of(
                        "Findings",
                        result.findings().isEmpty()
                            ? "(none)"
                            : renderedReviewFindings(result.findings())))),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.ATTESTATION_REVIEW));
        });
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
                              exported.operationOrder().toString(),
                              exported.operationHeadHex(),
                              exported.warnings()),
                          CliEnvelopeMapper.successArtifacts(
                              CliEnvelopeMapper.successArtifact(
                                  "attestation-receipt-v1", exported.receiptFilePath())))),
              () ->
                  writeText(
                      "Attestation Receipt Exported",
                      List.of(
                          List.of("Receipt file", CliTextDisplay.path(exported.receiptFilePath())),
                          List.of("Book ID", exported.bookId().toString()),
                          List.of("Operation order", exported.operationOrder().toString()),
                          List.of("Operation head", exported.operationHeadHex()),
                          List.of(
                              "Warnings",
                              exported.warnings().isEmpty()
                                  ? "(none)"
                                  : CliTextFormat.joined(exported.warnings())))),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.EXPORT_ATTESTATION_RECEIPT));
              });
      case ExportAttestationReceiptResult.AuthorizationRejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.attestationAuthorizationRejectedEnvelope(
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
                              valid.bookId().toString(),
                              valid.operationOrder().toString(),
                              valid.findings()))),
              () ->
                  writeText(
                      "Attestation Receipt Valid",
                      List.of(
                          List.of("Book ID", valid.bookId().toString()),
                          List.of("Operation order", valid.operationOrder().toString()),
                          List.of(
                              "Findings",
                              valid.findings().isEmpty()
                                  ? "(none)"
                                  : CliTextFormat.joined(valid.findings())))),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.VERIFY_RECEIPT));
              });
      case VerifyAttestationReceiptResult.Invalid invalid ->
          writeRejected(
              invalid.failureCode(),
              "The selected receipt is not valid for the selected book.",
              "Confirm the receipt and book paths refer to the intended independently retained evidence.",
              outputMode);
    }
  }

  private static List<CliAttestationJsonModels.AttestationReviewFindingPayload>
      reviewFindingPayloads(List<AttestationReviewFinding> findings) {
    return findings.stream()
        .map(
            finding -> {
              var review = finding.compromiseReview();
              return new CliAttestationJsonModels.AttestationReviewFindingPayload(
                  review.credentialKeyId(),
                  review.firstAffectedOrder().toString(),
                  review.lastAffectedOrder() == null ? null : review.lastAffectedOrder().toString(),
                  finding.operationOrder().toString());
            })
        .toList();
  }

  private static String renderedReviewFindings(List<AttestationReviewFinding> findings) {
    return CliTextFormat.joined(
        findings.stream()
            .map(
                finding -> {
                  var review = finding.compromiseReview();
                  return "credentialKeyId="
                      + review.credentialKeyId()
                      + ", firstAffectedOrder="
                      + review.firstAffectedOrder()
                      + ", lastAffectedOrder="
                      + (review.lastAffectedOrder() == null
                          ? "through-head"
                          : review.lastAffectedOrder())
                      + ", operationOrder="
                      + finding.operationOrder();
                })
            .toList());
  }

  private void writeRejected(String code, String message, String hint, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED, null, code, message, hint, null, null, null, null),
        outputMode);
  }

  private void writeText(String title, List<List<String>> rows) {
    outputChannel.writeText(
        CliTextFormat.renderTitledBlock(title, CliTextFormat.renderKeyValueBlock(rows)));
  }
}
