package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies PDF artifact failures retain their path facts and causal diagnostics after
 * serialization.
 */
class CliPdfArtifactExceptionSerializationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void outputDirectoryFailureRoundTripsItsDirectPathFact() throws Exception {
    Path requestedPath = temporaryDirectory.resolve("nested").resolve("..").resolve("report.pdf");

    CliArtifactOutputDirectoryException restored =
        roundTrip(
            new CliArtifactOutputDirectoryException(requestedPath, "--pdf-out", "PDF"),
            CliArtifactOutputDirectoryException.class);

    assertEquals(requestedPath.toAbsolutePath().normalize(), restored.outputPath());
    assertEquals("--pdf-out", restored.artifactOptionName());
    assertEquals("PDF", restored.artifactLabel());
    assertNull(restored.getCause());
  }

  @Test
  void outputDirectoryFailureRoundTripsItsCausalDiagnostic() throws Exception {
    Path requestedPath = temporaryDirectory.resolve("rejected.pdf");

    CliArtifactOutputDirectoryException restored =
        roundTrip(
            new CliArtifactOutputDirectoryException(
                requestedPath, "--pdf-out", "PDF", new IOException("private parent rejected")),
            CliArtifactOutputDirectoryException.class);

    assertEquals(requestedPath.toAbsolutePath().normalize(), restored.outputPath());
    IOException cause = assertInstanceOf(IOException.class, restored.getCause());
    assertEquals("private parent rejected", cause.getMessage());
  }

  @Test
  void outputExistsFailureRoundTripsItsCanonicalPathAndCause() throws Exception {
    Path requestedPath = temporaryDirectory.resolve("nested").resolve("..").resolve("existing.pdf");

    CliArtifactOutputExistsException restored =
        roundTrip(
            new CliArtifactOutputExistsException(
                requestedPath,
                "--pdf-out",
                new ArtifactPublicationRetention(
                    requestedPath.resolveSibling(".existing.pdf-stage")),
                new FileAlreadyExistsException(requestedPath.toString())),
            CliArtifactOutputExistsException.class);

    assertEquals(requestedPath.toAbsolutePath().normalize(), restored.outputPath());
    assertEquals("--pdf-out", restored.artifactOptionName());
    assertEquals(
        requestedPath.resolveSibling(".existing.pdf-stage").toAbsolutePath().normalize(),
        restored.retainedStage().retainedStagePath());
    FileAlreadyExistsException cause =
        assertInstanceOf(FileAlreadyExistsException.class, restored.getCause());
    assertEquals(requestedPath.toString(), cause.getFile());
  }

  @Test
  void pdfExportFailureRoundTripsItsCanonicalPathAndCause() throws Exception {
    Path requestedPath = temporaryDirectory.resolve("nested").resolve("..").resolve("failed.pdf");

    CliPdfExportException restored =
        roundTrip(
            new CliPdfExportException(requestedPath, new IOException("write failed")),
            CliPdfExportException.class);

    assertEquals(requestedPath.toAbsolutePath().normalize(), restored.outputPath());
    IOException cause = assertInstanceOf(IOException.class, restored.getCause());
    assertEquals("write failed", cause.getMessage());
  }

  private static <T extends Throwable> T roundTrip(T exception, Class<T> expectedType)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return expectedType.cast(input.readObject());
    }
  }
}
