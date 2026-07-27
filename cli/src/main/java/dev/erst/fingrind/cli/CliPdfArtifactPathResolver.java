package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Admits lexical no-follow PDF output locations before resolving their physical final path. */
final class CliPdfArtifactPathResolver {
  private final CliPdfReportExporter.OutputDirectoryAdmission outputDirectoryAdmission;

  CliPdfArtifactPathResolver(
      CliPdfReportExporter.OutputDirectoryAdmission outputDirectoryAdmission) {
    this.outputDirectoryAdmission =
        Objects.requireNonNull(outputDirectoryAdmission, "outputDirectoryAdmission");
  }

  Path resolve(Path outputPath) {
    Path normalizedOutputPath =
        Objects.requireNonNull(outputPath, "outputPath").toAbsolutePath().normalize();
    Path outputFileName = normalizedOutputPath.getFileName();
    if (outputFileName == null) {
      throw new CliArtifactOutputDirectoryException(normalizedOutputPath, "--pdf-out", "PDF");
    }
    try {
      Path parentDirectory = parentDirectory(normalizedOutputPath);
      if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
        outputDirectoryAdmission.require(parentDirectory);
      }
      Path canonicalParent = parentDirectory.toRealPath();
      outputDirectoryAdmission.require(canonicalParent);
      return canonicalParent.resolve(outputFileName);
    } catch (PrivateOutputDirectory.Violation exception) {
      throw new CliArtifactOutputDirectoryException(
          normalizedOutputPath, "--pdf-out", "PDF", exception);
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new CliPdfExportException(normalizedOutputPath, exception);
    }
  }

  static Path parentDirectory(Path outputPath) {
    Path parent = Objects.requireNonNull(outputPath, "outputPath").getParent();
    return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
  }
}
