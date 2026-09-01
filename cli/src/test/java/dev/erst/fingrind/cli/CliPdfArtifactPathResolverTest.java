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
    assertEquals(
        "The PDF output path must name a file beneath an existing private directory.",
        exception.publicMessage());
    assertEquals(
        "Choose a file path for --pdf-out beneath an existing private output directory, then rerun the command.",
        exception.publicHint());
  }

  @Test
  void resolveMapsGenericOutputAdmissionFailuresToTheOwnerOnlyRecovery() {
    CliArtifactOutputDirectoryException exception =
        new CliArtifactOutputDirectoryException(
            temporaryDirectory.resolve("trial-balance.pdf"),
            "--pdf-out",
            "PDF",
            new IOException("owner-only directory inspection failed"));

    assertEquals(
        "The PDF output parent must be an existing owner-only directory with non-mutable ancestry.",
        exception.publicMessage());
    assertEquals(
        "Create or select an existing owner-only directory for --pdf-out; on POSIX, restrict it to the owner (for example chmod 700), then rerun the command.",
        exception.publicHint());
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

  @Test
  void resolveSubmitsAMissingParentToOutputAdmissionBeforePhysicalResolution() {
    Path requestedOutputPath = temporaryDirectory.resolve("missing").resolve("report.pdf");
    java.util.concurrent.atomic.AtomicReference<Path> admitted =
        new java.util.concurrent.atomic.AtomicReference<>();
    CliPdfArtifactPathResolver resolver = new CliPdfArtifactPathResolver(admitted::set);

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> resolver.resolve(requestedOutputPath));

    assertEquals(requestedOutputPath.toAbsolutePath().normalize(), exception.outputPath());
    assertEquals(
        Objects.requireNonNull(requestedOutputPath.getParent(), "requested output parent")
            .toAbsolutePath()
            .normalize(),
        admitted.get());
  }

  @Test
  void parentDirectoryUsesTheCurrentDirectoryForAPathWithoutAParent() {
    assertEquals(
        Path.of(".").toAbsolutePath().normalize(),
        CliPdfArtifactPathResolver.parentDirectory(Path.of("report.pdf")));
  }
}
