package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests direct PDF artifact-path admission and physical-path resolution failures. */
class CliPdfArtifactPathResolverTest {
  @TempDir Path temporaryDirectory;

  @Test
  void resolveRejectsAFileSystemRootBecauseItCannotNameAnArtifact() {
    Path fileSystemRoot =
        Objects.requireNonNull(temporaryDirectory.getRoot(), "temporaryDirectory root");
    CliPdfArtifactPathResolver resolver = new CliPdfArtifactPathResolver(ignored -> {});

    CliArtifactOutputDirectoryException exception =
        assertThrows(
            CliArtifactOutputDirectoryException.class, () -> resolver.resolve(fileSystemRoot));

    assertEquals(fileSystemRoot.toAbsolutePath().normalize(), exception.outputPath());
    assertEquals("--pdf-out", exception.artifactOptionName());
  }

  @Test
  void resolvePreservesAnOutputAdmissionIoFailureAsAPdfExportFailure() {
    IOException failure = new IOException("private output directory inspection failed");
    CliPdfArtifactPathResolver resolver =
        new CliPdfArtifactPathResolver(
            ignored -> {
              throw failure;
            });
    Path requestedOutputPath = temporaryDirectory.resolve("trial-balance.pdf");

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> resolver.resolve(requestedOutputPath));

    assertEquals(requestedOutputPath.toAbsolutePath().normalize(), exception.outputPath());
    assertSame(failure, exception.getCause());
  }
}
