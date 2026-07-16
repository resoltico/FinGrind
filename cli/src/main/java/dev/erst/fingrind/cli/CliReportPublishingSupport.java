package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Shared publication path for report models across CLI read and query surfaces. */
final class CliReportPublishingSupport {
  private CliReportPublishingSupport() {}

  static void writeReportedModel(
      CliOutputChannel outputChannel,
      ReportModel reportModel,
      CliReportJsonModels.ReportPayload reportPayload,
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(reportPayload, exportedArtifactPath)),
        () ->
            outputChannel.writeText(
                exportedArtifactPath == null
                    ? TextReportProjector.render(reportModel)
                    : CliArtifactOutputRenderer.renderPdfArtifact(exportedArtifactPath)),
        () -> {
          if (exportedArtifactPath != null) {
            throw new IllegalStateException(
                "CSV stdout cannot be combined with --pdf-out after argument validation.");
          }
          outputChannel.writeText(CliSemanticReportCsvRenderer.render(reportPayload));
        });
  }
}
