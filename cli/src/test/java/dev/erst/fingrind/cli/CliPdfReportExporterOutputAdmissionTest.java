package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.CLOCK;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.exporterWithoutNativeDirectoryForce;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.privatePdfOutputDirectory;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests output-destination admission before PDF publication starts. */
class CliPdfReportExporterOutputAdmissionTest {
  @TempDir Path tempDirectory;

  @Test
  void exportRefusesOutputParentOnFilesystemWithoutPrivatePermissionModel() throws IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.57.0", CLOCK));
    Path archivePath = tempDirectory.resolve("reports.zip");

    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + archivePath.toUri()), Map.of("create", "true"))) {
      Path trialBalancePdf = zipFileSystem.getPath("/reports/trial-balance.pdf");
      Files.createDirectories(trialBalancePdf.getParent());

      CliArtifactOutputDirectoryException exception =
          assertThrows(
              CliArtifactOutputDirectoryException.class,
              () ->
                  exporter.export(
                      trialBalancePdf,
                      TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

      assertEquals(trialBalancePdf.toAbsolutePath().normalize(), exception.outputPath());
      assertEquals("--pdf-out", exception.artifactOptionName());
    }
  }

  @Test
  void exportRefusesOutputParentThatIsNotAnExistingRealDirectory() throws IOException {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(new PdfReportService("FinGrind", "0.57.0", CLOCK));
    Path blockedParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(blockedParent, "nope", StandardCharsets.UTF_8);
    Path outputPath = blockedParent.resolve("trial-balance.pdf");

    CliArtifactOutputDirectoryException exception =
        assertThrows(
            CliArtifactOutputDirectoryException.class,
            () ->
                exporter.export(
                    outputPath, TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
  }

  @Test
  void exportRejectsExistingArtifactDestinationThroughNoClobberLink() throws IOException {
    CliPdfReportExporter exporter = exporterWithoutNativeDirectoryForce();
    Path outputPath =
        privatePdfOutputDirectory(tempDirectory, "existing-artifact").resolve("trial-balance.pdf");
    Files.writeString(outputPath, "occupied", StandardCharsets.UTF_8);

    CliArtifactOutputExistsException exception =
        assertThrows(
            CliArtifactOutputExistsException.class,
            () ->
                exporter.export(
                    outputPath, TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(outputPath.toRealPath(), exception.outputPath());
    assertEquals("--pdf-out", exception.artifactOptionName());
    assertEquals("occupied", Files.readString(outputPath, StandardCharsets.UTF_8));
  }

  @Test
  void exportRefusesAnIntermediateOutputDirectoryAliasBeforeCreatingAStage() throws IOException {
    Path physicalOutputDirectory = privatePdfOutputDirectory(tempDirectory, "physical-pdf-output");
    Path alias = tempDirectory.resolve("pdf-output-alias");
    Files.createSymbolicLink(alias, tempDirectory);
    Path requestedOutputPath = alias.resolve("physical-pdf-output/trial-balance.pdf");
    Path physicalOutputPath = physicalOutputDirectory.resolve("trial-balance.pdf");

    CliArtifactOutputDirectoryException exception =
        assertThrows(
            CliArtifactOutputDirectoryException.class,
            () ->
                exporterWithoutNativeDirectoryForce()
                    .export(
                        requestedOutputPath,
                        TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(requestedOutputPath.toAbsolutePath().normalize(), exception.outputPath());
    assertFalse(Files.exists(physicalOutputPath));
  }

  @Test
  void exportRefusesDirectOutputParentAliasBeforeCreatingAStage() throws IOException {
    Path physicalOutputDirectory =
        privatePdfOutputDirectory(tempDirectory, "physical-direct-pdf-output");
    Path directParentAlias = tempDirectory.resolve("direct-pdf-output-alias");
    Files.createSymbolicLink(directParentAlias, physicalOutputDirectory);
    Path outputPath = directParentAlias.resolve("trial-balance.pdf");

    CliArtifactOutputDirectoryException exception =
        assertThrows(
            CliArtifactOutputDirectoryException.class,
            () ->
                exporterWithoutNativeDirectoryForce()
                    .export(
                        outputPath,
                        TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
    assertFalse(Files.exists(physicalOutputDirectory.resolve("trial-balance.pdf")));
  }

  @Test
  void exportRefusesGroupWritableOutputParentBeforeCreatingAStage() throws IOException {
    Assumptions.assumeTrue(
        tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path unsafeOutputDirectory = tempDirectory.resolve("group-writable-pdf-output");
    Files.createDirectories(unsafeOutputDirectory);
    Files.setPosixFilePermissions(
        unsafeOutputDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE));
    Path outputPath = unsafeOutputDirectory.resolve("trial-balance.pdf");

    CliArtifactOutputDirectoryException exception =
        assertThrows(
            CliArtifactOutputDirectoryException.class,
            () ->
                new CliPdfReportExporter(new PdfReportService("FinGrind", "0.57.0", CLOCK))
                    .export(
                        outputPath,
                        TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
  }
}
