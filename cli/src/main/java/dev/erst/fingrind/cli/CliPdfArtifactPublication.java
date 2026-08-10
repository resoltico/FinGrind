package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Publishes one rendered PDF through the canonical publication-transaction owner. */
final class CliPdfArtifactPublication {
  private static final String PDF_REPORT_MEMBER_ID = "pdf-report";

  private final Path canonicalOutputPath;
  private final PublicationTransactionService publicationTransactions;

  CliPdfArtifactPublication(Path canonicalOutputPath) throws IOException {
    this(canonicalOutputPath, PublicationTransactionPublisher.openCanonical());
  }

  CliPdfArtifactPublication(
      Path canonicalOutputPath, PublicationTransactionService publicationTransactions) {
    this.canonicalOutputPath =
        Objects.requireNonNull(canonicalOutputPath, "canonicalOutputPath")
            .toAbsolutePath()
            .normalize();
    this.publicationTransactions =
        Objects.requireNonNull(publicationTransactions, "publicationTransactions");
  }

  PublicationTransactionArtifact publish(byte[] pdfBytes) {
    try {
      return new PublicationTransactionArtifact(
          canonicalOutputPath,
          publicationTransactions.publish(
              new PublicationTransactionRequest(
                  List.of(
                      new PublicationTransactionMemberRequest(
                          PDF_REPORT_MEMBER_ID,
                          PublicationTransactionMemberRole.PDF_REPORT,
                          canonicalOutputPath,
                          PublicationMode.NO_REPLACE_LINK,
                          Objects.requireNonNull(pdfBytes, "pdfBytes"))))));
    } catch (IOException exception) {
      throw new CliPdfExportException(canonicalOutputPath, exception);
    }
  }
}
