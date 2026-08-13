package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** CLI adapter that renders reports and delegates protected artifact publication. */
final class CliPdfReportExporter {
  private final PdfReportService pdfReportService;
  private final CliPdfArtifactPublisher artifactPublisher;

  CliPdfReportExporter(PdfReportService pdfReportService) {
    this(
        pdfReportService,
        PrivateOutputDirectory::requireExistingOwnerOnly,
        PublicationTransactionPublisher::openCanonical);
  }

  CliPdfReportExporter(
      PdfReportService pdfReportService,
      OutputDirectoryAdmission outputDirectoryAdmission,
      PublicationTransactionServiceFactory publicationTransactionServiceFactory) {
    this.pdfReportService = Objects.requireNonNull(pdfReportService, "pdfReportService");
    this.artifactPublisher =
        new CliPdfArtifactPublisher(
            Objects.requireNonNull(outputDirectoryAdmission, "outputDirectoryAdmission"),
            Objects.requireNonNull(
                publicationTransactionServiceFactory, "publicationTransactionServiceFactory"));
  }

  PublicationTransactionArtifact export(Path outputPath, ReportModel reportModel) {
    return artifactPublisher.publish(outputPath, pdfReportService.render(reportModel));
  }

  static Path parentDirectory(Path outputPath) {
    return CliPdfArtifactPathResolver.parentDirectory(outputPath);
  }

  /** Admits a parent directory before a PDF exporter creates any staged artifact inside it. */
  @FunctionalInterface
  interface OutputDirectoryAdmission {
    /** Requires a real private output directory with secure resolved ancestry. */
    void require(Path directory) throws IOException;
  }

  /**
   * Opens the canonical transaction authority without exposing filesystem operations to callers.
   */
  @FunctionalInterface
  interface PublicationTransactionServiceFactory {
    /** Opens the only authority that may stage, commit, clean, or recover publication artifacts. */
    PublicationTransactionService open() throws IOException;
  }
}
