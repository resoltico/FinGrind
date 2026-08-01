package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.nio.file.Path;
import java.util.Objects;

/** Owns one protected PDF artifact's path admission and staged publication boundary. */
final class CliPdfArtifactPublisher {
  private final CliPdfReportExporter.FileOperations fileOperations;
  private final CliPdfArtifactPathResolver pathResolver;

  CliPdfArtifactPublisher(
      CliPdfReportExporter.FileOperations fileOperations,
      CliPdfReportExporter.OutputDirectoryAdmission outputDirectoryAdmission) {
    this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
    this.pathResolver = new CliPdfArtifactPathResolver(outputDirectoryAdmission);
  }

  ArtifactPublicationResult publish(Path outputPath, byte[] pdfBytes) {
    return new CliPdfArtifactPublication(pathResolver.resolve(outputPath), fileOperations)
        .publish(pdfBytes);
  }
}
