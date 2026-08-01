package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Publishes one rendered PDF through a private stage and an exact no-clobber final link. */
final class CliPdfArtifactPublication {
  private static final String STAGED_PDF_PREFIX = ".fingrind-pdf-";
  private static final String STAGED_PDF_SUFFIX = ".tmp";

  private final Path canonicalOutputPath;
  private final Path outputParent;
  private final CliPdfReportExporter.FileOperations fileOperations;

  CliPdfArtifactPublication(
      Path canonicalOutputPath, CliPdfReportExporter.FileOperations fileOperations) {
    this.canonicalOutputPath =
        Objects.requireNonNull(canonicalOutputPath, "canonicalOutputPath")
            .toAbsolutePath()
            .normalize();
    this.outputParent =
        Objects.requireNonNull(this.canonicalOutputPath.getParent(), "canonical PDF output parent");
    this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
  }

  ArtifactPublicationResult publish(byte[] pdfBytes) {
    ArtifactPublicationRetention retention = stage(Objects.requireNonNull(pdfBytes, "pdfBytes"));
    createFinalLink(retention);
    return confirmPublishedArtifact(retention);
  }

  private ArtifactPublicationRetention stage(byte[] pdfBytes) {
    try {
      return new ArtifactPublicationRetention(
          fileOperations.createAndWriteStage(
              outputParent, STAGED_PDF_PREFIX, STAGED_PDF_SUFFIX, pdfBytes));
    } catch (ArtifactPublicationRetainedStageException exception) {
      throw new CliPdfExportException(canonicalOutputPath, exception);
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new CliPdfExportException(canonicalOutputPath, exception);
    }
  }

  private void createFinalLink(ArtifactPublicationRetention retention) {
    try {
      fileOperations.createLink(canonicalOutputPath, retention.retainedStagePath());
    } catch (FileAlreadyExistsException exception) {
      throw new CliArtifactOutputExistsException(
          canonicalOutputPath, "--pdf-out", retention, exception);
    } catch (IOException | RuntimeException exception) {
      throw new CliPdfExportException(
          canonicalOutputPath,
          new ArtifactPublicationOutcomeUncertainException(
              canonicalOutputPath, retention, exception));
    } catch (Error exception) {
      retainStageAfterError(retention, exception);
      throw exception;
    }
  }

  private ArtifactPublicationResult confirmPublishedArtifact(
      ArtifactPublicationRetention retention) {
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(canonicalOutputPath, retention);
    try {
      fileOperations.forceDirectory(outputParent);
      return publication;
    } catch (IOException | RuntimeException exception) {
      throw new CliPdfPublicationDurabilityException(publication, exception);
    } catch (Error exception) {
      retainStageAfterError(retention, exception);
      throw exception;
    }
  }

  private static void retainStageAfterError(
      ArtifactPublicationRetention retention, Error primaryFailure) {
    primaryFailure.addSuppressed(
        new ArtifactPublicationRetainedStageException(
            retention,
            new IOException(
                "Fatal PDF publication failure retained the exact private artifact stage.")));
  }
}
