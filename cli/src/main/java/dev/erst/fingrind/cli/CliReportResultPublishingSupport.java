package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryReportResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TaxObligationReportModelBuilder;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Centralizes generic reported and rejected envelope publication for report-family writers. */
final class CliReportResultPublishingSupport {
  private CliReportResultPublishingSupport() {}

  static <REPORTED> void write(
      CliOutputChannel outputChannel,
      BookQueryReportResult<REPORTED> result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt,
      CliReportProjection<REPORTED> projection) {
    REPORTED reported = result.reported();
    if (reported == null) {
      writeRejected(
          outputChannel, Objects.requireNonNull(result.rejection(), "rejection"), outputMode);
      return;
    }
    ReportModel reportModel = projection.reportModelBuilder().apply(reported);
    CliReportPublishingSupport.writeReportedModel(
        outputChannel,
        reportModel,
        projection.reportPayloadBuilder().apply(reported, generatedAt),
        outputMode,
        exportedArtifact);
  }

  static void writeTaxObligation(
      CliOutputChannel outputChannel,
      TaxObligationResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    switch (result) {
      case TaxObligationResult.Reported reported ->
          CliReportPublishingSupport.writeReportedModel(
              outputChannel,
              TaxObligationReportModelBuilder.buildModel(reported.report()),
              CliReportPayloadMapper.taxObligation(reported.report(), generatedAt),
              outputMode,
              exportedArtifact);
      case TaxObligationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxQueryRejectedEnvelope(
                  OperationId.TAX_OBLIGATION, rejected.rejection()),
              outputMode);
    }
  }

  private static void writeRejected(
      CliOutputChannel outputChannel, BookQueryRejection rejection, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.queryRejectedEnvelope(rejection), outputMode);
  }
}
