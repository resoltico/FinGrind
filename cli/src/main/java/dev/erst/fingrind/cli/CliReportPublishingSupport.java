package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import org.jspecify.annotations.Nullable;

/** Shared publication path for report models across CLI read and query surfaces. */
final class CliReportPublishingSupport {
  private CliReportPublishingSupport() {}

  static void writeReportedModel(
      CliOutputChannel outputChannel,
      ReportModel reportModel,
      CliReportJsonModels.ReportPayload reportPayload,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(reportPayload, exportedArtifact)),
        () ->
            outputChannel.writeText(
                exportedArtifact == null
                    ? TextReportProjector.render(reportModel)
                    : CliArtifactOutputRenderer.renderPdfArtifact(exportedArtifact)),
        () -> {
          if (exportedArtifact != null) {
            throw new IllegalStateException(
                "CSV stdout cannot be combined with --pdf-out after argument validation.");
          }
          outputChannel.writeText(CliSemanticReportCsvRenderer.render(reportPayload));
        });
  }
}
