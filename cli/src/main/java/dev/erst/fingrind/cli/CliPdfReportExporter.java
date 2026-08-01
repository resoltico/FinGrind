package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationStages;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** CLI adapter that renders reports and delegates protected artifact publication. */
final class CliPdfReportExporter {
  private final PdfReportService pdfReportService;
  private final CliPdfArtifactPublisher artifactPublisher;

  CliPdfReportExporter(PdfReportService pdfReportService) {
    this(
        pdfReportService,
        new DefaultFileOperations(),
        PrivateOutputDirectory::requireExistingOwnerOnly);
  }

  CliPdfReportExporter(PdfReportService pdfReportService, FileOperations fileOperations) {
    this(pdfReportService, fileOperations, PrivateOutputDirectory::requireExistingOwnerOnly);
  }

  CliPdfReportExporter(
      PdfReportService pdfReportService,
      FileOperations fileOperations,
      OutputDirectoryAdmission outputDirectoryAdmission) {
    this.pdfReportService = Objects.requireNonNull(pdfReportService, "pdfReportService");
    this.artifactPublisher =
        new CliPdfArtifactPublisher(
            Objects.requireNonNull(fileOperations, "fileOperations"),
            Objects.requireNonNull(outputDirectoryAdmission, "outputDirectoryAdmission"));
  }

  ArtifactPublicationResult export(Path outputPath, ReportModel reportModel) {
    return artifactPublisher.publish(outputPath, pdfReportService.render(reportModel));
  }

  static Path parentDirectory(Path outputPath) {
    return CliPdfArtifactPathResolver.parentDirectory(outputPath);
  }

  /** One filesystem adapter used by PDF export and its focused unit tests. */
  interface FileOperations {
    /** Creates, force-writes, and retains one exact owner-private stage through a bound channel. */
    Path createAndWriteStage(Path directory, String prefix, String suffix, byte[] bytes)
        throws IOException;

    /** Creates the final no-clobber artifact name as a link to the completed retained stage. */
    void createLink(Path finalPath, Path stagedPath) throws IOException;

    /** Forces the committed final directory entry before publication success is reported. */
    void forceDirectory(Path directory) throws IOException;
  }

  /** Admits a parent directory before a PDF exporter creates any staged artifact inside it. */
  @FunctionalInterface
  interface OutputDirectoryAdmission {
    /** Requires a real private output directory with secure resolved ancestry. */
    void require(Path directory) throws IOException;
  }

  /** Default {@code java.nio.file.Files} implementation for real CLI PDF export. */
  static final class DefaultFileOperations implements FileOperations {
    @Override
    public Path createAndWriteStage(Path directory, String prefix, String suffix, byte[] bytes)
        throws IOException {
      return ArtifactPublicationStages.createAndWrite(directory, prefix, suffix, bytes);
    }

    @Override
    public void createLink(Path finalPath, Path stagedPath) throws IOException {
      Files.createLink(finalPath, stagedPath);
    }

    @Override
    public void forceDirectory(Path directory) throws IOException {
      AttestationDirectoryDurability.force(directory);
    }
  }
}
