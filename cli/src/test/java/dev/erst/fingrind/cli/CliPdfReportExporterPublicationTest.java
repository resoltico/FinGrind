package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.exporterWith;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.RecordingPublicationTransactions;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests PDF publication's transaction boundary and recovery-only failure contract. */
class CliPdfReportExporterPublicationTest {
  private static final Path OUTPUT_PATH = Path.of("trial-balance.pdf").toAbsolutePath().normalize();

  @TempDir Path temporaryDirectory;

  @Test
  void exportReportsOnlyTheCompletedTransactionAndFinalArtifact() {
    RecordingPublicationTransactions publicationTransactions =
        new RecordingPublicationTransactions();

    var publication = export(publicationTransactions);

    assertEquals(OUTPUT_PATH, publication.publishedArtifactPath());
    assertTrue(publication.transactionResult().successful());
    assertEquals(
        "0123456789abcdef0123456789abcdef",
        publication.transactionResult().transactionId().value());
    assertEquals(
        1,
        Objects.requireNonNull(publicationTransactions.publishedRequest, "publishedRequest")
            .members()
            .size());
    assertTrue(
        Objects.requireNonNull(publicationTransactions.publishedRequest, "publishedRequest")
            .members()
            .getFirst()
            .toString()
            .contains("secretPayload=<redacted>"));
  }

  @Test
  void exportMapsAnIncompleteTransactionToIdOnlyRecoveryDetails() {
    RecordingPublicationTransactions publicationTransactions =
        new RecordingPublicationTransactions();
    PublicationTransactionResult incompleteResult =
        new PublicationTransactionResult(
            new PublicationTransactionId("fedcba9876543210fedcba9876543210"),
            PublicationTransactionState.COMMIT_UNCERTAIN,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.INCOMPLETE));
    publicationTransactions.publishFailure =
        new PublicationTransactionExecutionException(
            incompleteResult, new IOException("final artifact outcome is unknown"));

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> export(publicationTransactions));
    CliFailure failure =
        Objects.requireNonNull(CliFailureMapper.runtimeFailure(exception), "mapped CLI failure");

    assertEquals("pdf-export-failure", failure.code());
    assertEquals(OUTPUT_PATH, failure.path());
    assertEquals(java.util.List.of(), failure.relatedPaths());
    assertNull(failure.retainedStage());
    CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails details =
        assertInstanceOf(
            CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails.class,
            failure.details());
    assertEquals(OUTPUT_PATH.toString(), details.candidateArtifact());
    assertEquals("fedcba9876543210fedcba9876543210", details.publicationTransaction().id());
    assertEquals("commit-uncertain", details.publicationTransaction().state());
    assertEquals("commit-uncertain", details.publicationTransaction().commitOutcome());
    assertEquals("incomplete", details.publicationTransaction().cleanupOutcome());
  }

  @Test
  void exportWrapsTransactionAuthorityStartupFailureAtThePdfBoundary() {
    CliPdfReportExporter exporter =
        new CliPdfReportExporter(
            new dev.erst.fingrind.report.pdf.PdfReportService(
                "FinGrind", "0.57.0", CliPdfReportExporterTestSupport.CLOCK),
            ignored -> {},
            () -> {
              throw new IOException("canonical publication authority is unavailable");
            });

    CliPdfExportException exception =
        assertThrows(
            CliPdfExportException.class,
            () ->
                exporter.export(
                    OUTPUT_PATH, TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(OUTPUT_PATH, exception.outputPath());
    IOException cause = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("canonical publication authority is unavailable", cause.getMessage());
  }

  @Test
  void exportRefusesAnExistingPdfBeforeOpeningThePublicationTransaction() throws IOException {
    Path outputPath = temporaryDirectory.resolve("existing.pdf");
    Files.writeString(outputPath, "existing artifact");
    RecordingPublicationTransactions publicationTransactions =
        new RecordingPublicationTransactions();

    CliPdfOutputTargetOccupiedException exception =
        assertThrows(
            CliPdfOutputTargetOccupiedException.class,
            () ->
                exporterWith(publicationTransactions)
                    .export(
                        outputPath,
                        TrialBalanceReportModelBuilder.buildModel(trialBalanceReport())));

    assertEquals(outputPath.toAbsolutePath().normalize(), exception.outputPath());
    assertNull(publicationTransactions.publishedRequest);
  }

  private static dev.erst.fingrind.core.PublicationTransactionArtifact export(
      RecordingPublicationTransactions publicationTransactions) {
    return exporterWith(publicationTransactions)
        .export(OUTPUT_PATH, TrialBalanceReportModelBuilder.buildModel(trialBalanceReport()));
  }
}
