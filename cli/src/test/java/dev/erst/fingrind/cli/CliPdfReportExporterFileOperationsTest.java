package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.privatePdfOutputDirectory;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the durable retained-stage operations used by PDF export publication. */
class CliPdfReportExporterFileOperationsTest {
  @TempDir Path tempDirectory;

  @Test
  void defaultFileOperationsCreateAndForceAnOwnerPrivateRetainedStage() throws IOException {
    Path outputDirectory = privatePdfOutputDirectory(tempDirectory, "reports");
    byte[] expectedPdf = "%PDF-retained-stage".getBytes(StandardCharsets.ISO_8859_1);

    Path retainedStage =
        new CliPdfReportExporter.DefaultFileOperations()
            .createAndWriteStage(outputDirectory, ".fingrind-pdf-", ".tmp", expectedPdf);

    assertEquals(
        outputDirectory.toRealPath(),
        java.util.Objects.requireNonNull(retainedStage.getParent(), "retained stage parent")
            .toRealPath());
    assertTrue(retainedStage.getFileName().toString().startsWith(".fingrind-pdf-"));
    assertTrue(retainedStage.getFileName().toString().endsWith(".tmp"));
    assertArrayEquals(expectedPdf, Files.readAllBytes(retainedStage));
    if (retainedStage.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(retainedStage));
    }
  }

  @Test
  void defaultFileOperationsNeverReuseAnExistingRetainedStage() throws IOException {
    Path outputDirectory = privatePdfOutputDirectory(tempDirectory, "reports");
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations();

    Path firstStage =
        fileOperations.createAndWriteStage(
            outputDirectory, ".fingrind-pdf-", ".tmp", new byte[] {1, 3, 5});
    Path secondStage =
        fileOperations.createAndWriteStage(
            outputDirectory, ".fingrind-pdf-", ".tmp", new byte[] {2, 4, 6});

    assertNotEquals(firstStage, secondStage);
    assertArrayEquals(new byte[] {1, 3, 5}, Files.readAllBytes(firstStage));
    assertArrayEquals(new byte[] {2, 4, 6}, Files.readAllBytes(secondStage));
  }

  @Test
  void defaultFileOperationsCreateTheFinalArtifactAsALinkToItsRetainedStage() throws IOException {
    Path outputDirectory = privatePdfOutputDirectory(tempDirectory, "reports");
    CliPdfReportExporter.DefaultFileOperations fileOperations =
        new CliPdfReportExporter.DefaultFileOperations();
    Path retainedStage =
        fileOperations.createAndWriteStage(
            outputDirectory,
            ".fingrind-pdf-",
            ".tmp",
            "%PDF-link".getBytes(StandardCharsets.ISO_8859_1));
    Path finalArtifact = outputDirectory.resolve("trial-balance.pdf");

    fileOperations.createLink(finalArtifact, retainedStage);

    assertTrue(Files.isSameFile(finalArtifact, retainedStage));
    assertArrayEquals(Files.readAllBytes(retainedStage), Files.readAllBytes(finalArtifact));
  }

  @Test
  void parentDirectoryFallsBackToCurrentWorkingDirectoryWhenPathHasNoParent() {
    assertEquals(
        Path.of(".").toAbsolutePath().normalize(),
        CliPdfReportExporter.parentDirectory(Path.of("trial-balance.pdf")));
  }

  @Test
  void retainedStagesAreNeverDeletedByTheFileOperationsContract() {
    assertFalse(
        java.util.Arrays.stream(CliPdfReportExporter.FileOperations.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .anyMatch(name -> name.contains("delete") || name.contains("cleanup")));
  }
}
