package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Owns one protected PDF artifact's path admission and transaction publication boundary. */
final class CliPdfArtifactPublisher {
  private final CliPdfArtifactPathResolver pathResolver;
  private final CliPdfReportExporter.PublicationTransactionServiceFactory
      publicationTransactionServiceFactory;

  CliPdfArtifactPublisher(
      CliPdfReportExporter.OutputDirectoryAdmission outputDirectoryAdmission,
      CliPdfReportExporter.PublicationTransactionServiceFactory
          publicationTransactionServiceFactory) {
    this.pathResolver = new CliPdfArtifactPathResolver(outputDirectoryAdmission);
    this.publicationTransactionServiceFactory =
        Objects.requireNonNull(
            publicationTransactionServiceFactory, "publicationTransactionServiceFactory");
  }

  PublicationTransactionArtifact publish(Path outputPath, byte[] pdfBytes) {
    Path canonicalOutputPath = pathResolver.resolve(outputPath);
    try {
      PublicationTransactionService publicationTransactions =
          publicationTransactionServiceFactory.open();
      return new CliPdfArtifactPublication(canonicalOutputPath, publicationTransactions)
          .publish(pdfBytes);
    } catch (IOException exception) {
      throw new CliPdfExportException(canonicalOutputPath, exception);
    }
  }
}
