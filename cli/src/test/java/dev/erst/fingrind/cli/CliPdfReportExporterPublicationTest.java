package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.exporterWith;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.RecordingFileOperations;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Tests staged PDF publication, retained evidence, and durability boundaries. */
class CliPdfReportExporterPublicationTest {
  private static final Path OUTPUT_PATH = Path.of("trial-balance.pdf").toAbsolutePath().normalize();

  @Test
  void exportPublishesAFinalLinkAndReportsTheExactRetainedStage() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();

    ArtifactPublicationResult publication = export(fileOperations);

    assertTrue(fileOperations.observations.stageCreatedAndWritten);
    assertTrue(fileOperations.observations.linkAttempted);
    assertTrue(fileOperations.observations.linkCreated);
    assertEquals(1, fileOperations.observations.directoryForceCount);
    assertEquals(OUTPUT_PATH, publication.publishedArtifactPath());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        publication.retention().retainedStagePath());
    assertNotEquals(
        publication.publishedArtifactPath(), publication.retention().retainedStagePath());
    assertTrue(fileOperations.stageBytes().length > 5);
    assertEquals((byte) '%', fileOperations.stageBytes()[0]);
  }

  @Test
  void exportReportsNoClobberCollisionTogetherWithItsRetainedStage() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failures.failDuringLinkWithExistingTarget = true;

    CliArtifactOutputExistsException exception =
        assertThrows(CliArtifactOutputExistsException.class, () -> export(fileOperations));

    assertEquals(OUTPUT_PATH, exception.outputPath());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        exception.retainedStage().retainedStagePath());
    assertTrue(fileOperations.observations.stageCreatedAndWritten);
    assertTrue(fileOperations.observations.linkAttempted);
    assertFalse(fileOperations.observations.linkCreated);
    assertEquals(0, fileOperations.observations.directoryForceCount);

    CliFailure failure =
        Objects.requireNonNull(CliFailureMapper.runtimeFailure(exception), "mapped CLI failure");
    assertEquals("artifact-output-already-exists", failure.code());
    assertEquals(OUTPUT_PATH, failure.path());
    assertEquals(
        List.of(fileOperations.stagedPath().toAbsolutePath().normalize()), failure.relatedPaths());
    assertEquals(fileOperations.stagedPath().toAbsolutePath().normalize(), failure.retainedStage());
  }

  @Test
  void exportReportsFinalDirectoryDurabilityUncertaintyWithFullPublicationEvidence() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failures.failOnDirectoryForceAttempt = 1;

    CliPdfPublicationDurabilityException exception =
        assertThrows(CliPdfPublicationDurabilityException.class, () -> export(fileOperations));

    assertInstanceOf(IOException.class, exception.getCause());
    assertTrue(fileOperations.observations.linkCreated);
    assertEquals(1, fileOperations.observations.directoryForceCount);
    assertEquals(OUTPUT_PATH, exception.publication().publishedArtifactPath());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        exception.publication().retention().retainedStagePath());

    CliFailure failure =
        Objects.requireNonNull(CliFailureMapper.runtimeFailure(exception), "mapped CLI failure");
    assertEquals("artifact-publication-durability-uncertain", failure.code());
    assertEquals(OUTPUT_PATH, failure.path());
    assertEquals(fileOperations.stagedPath().toAbsolutePath().normalize(), failure.retainedStage());
    CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails details =
        assertInstanceOf(
            CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails.class,
            failure.details());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize().toString(),
        details.publishedArtifact().retainedStage());
  }

  @Test
  void exportPreservesAStageWriteFailureAndItsRetainedEvidenceWithoutAttemptingTheLink() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    IOException primaryFailure = new IOException("staged PDF write failed");
    fileOperations.failures.failureAfterStageCreation = primaryFailure;

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> export(fileOperations));

    ArtifactPublicationRetainedStageException retainedFailure =
        assertInstanceOf(ArtifactPublicationRetainedStageException.class, exception.getCause());
    assertSame(primaryFailure, retainedFailure.getCause());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        retainedFailure.retainedStage().retainedStagePath());
    assertFalse(fileOperations.observations.linkAttempted);
    assertEquals(0, fileOperations.observations.directoryForceCount);

    CliFailure failure =
        Objects.requireNonNull(CliFailureMapper.runtimeFailure(exception), "mapped CLI failure");
    assertEquals("pdf-export-failure", failure.code());
    assertEquals(OUTPUT_PATH, failure.path());
    assertEquals(
        List.of(fileOperations.stagedPath().toAbsolutePath().normalize()), failure.relatedPaths());
    assertEquals(fileOperations.stagedPath().toAbsolutePath().normalize(), failure.retainedStage());
  }

  @Test
  void exportReportsIndeterminateFinalLinkOutcomeWithTheRetainedStage() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    fileOperations.failures.failDuringLink = true;

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> export(fileOperations));

    ArtifactPublicationOutcomeUncertainException outcome =
        assertInstanceOf(ArtifactPublicationOutcomeUncertainException.class, exception.getCause());
    assertEquals(OUTPUT_PATH, outcome.candidateArtifactPath());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        Objects.requireNonNull(outcome.retainedStage(), "retained stage").retainedStagePath());
    assertTrue(fileOperations.observations.linkAttempted);
    assertFalse(fileOperations.observations.linkCreated);
    assertEquals(0, fileOperations.observations.directoryForceCount);

    CliFailure failure =
        Objects.requireNonNull(CliFailureMapper.runtimeFailure(exception), "mapped CLI failure");
    assertEquals("artifact-publication-outcome-uncertain", failure.code());
    assertEquals(OUTPUT_PATH, failure.path());
    assertEquals(
        List.of(fileOperations.stagedPath().toAbsolutePath().normalize()), failure.relatedPaths());
    assertEquals(fileOperations.stagedPath().toAbsolutePath().normalize(), failure.retainedStage());
    CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails details =
        assertInstanceOf(
            CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails.class,
            failure.details());
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize().toString(),
        details.retainedStage());
  }

  @Test
  void exportRethrowsFatalLinkFailureWithRetainedStageEvidenceSuppressedOnThePrimaryError() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    AssertionError primaryFailure = new AssertionError("final PDF link failed");
    fileOperations.failures.errorDuringLink = primaryFailure;

    AssertionError exception = assertThrows(AssertionError.class, () -> export(fileOperations));

    assertSame(primaryFailure, exception);
    ArtifactPublicationRetainedStageException retainedFailure =
        assertInstanceOf(
            ArtifactPublicationRetainedStageException.class, exception.getSuppressed()[0]);
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        retainedFailure.retainedStage().retainedStagePath());
    assertTrue(fileOperations.observations.linkAttempted);
    assertFalse(fileOperations.observations.linkCreated);
    assertEquals(0, fileOperations.observations.directoryForceCount);
  }

  @Test
  void exportRethrowsFatalDurabilityFailureWithRetainedStageEvidenceSuppressedOnThePrimaryError() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    AssertionError primaryFailure = new AssertionError("directory force failed");
    fileOperations.failures.errorOnDirectoryForceAttempt = 1;
    fileOperations.failures.errorDuringDirectoryForce = primaryFailure;

    AssertionError exception = assertThrows(AssertionError.class, () -> export(fileOperations));

    assertSame(primaryFailure, exception);
    ArtifactPublicationRetainedStageException retainedFailure =
        assertInstanceOf(
            ArtifactPublicationRetainedStageException.class, exception.getSuppressed()[0]);
    assertEquals(
        fileOperations.stagedPath().toAbsolutePath().normalize(),
        retainedFailure.retainedStage().retainedStagePath());
    assertTrue(fileOperations.observations.linkCreated);
    assertEquals(1, fileOperations.observations.directoryForceCount);
  }

  @Test
  void exportReportsAStageAllocationFailureWithoutInventingRetainedEvidence() {
    RecordingFileOperations fileOperations = new RecordingFileOperations();
    IOException primaryFailure = new IOException("private PDF stage unavailable");
    fileOperations.failures.failureBeforeStageCreation = primaryFailure;

    CliPdfExportException exception =
        assertThrows(CliPdfExportException.class, () -> export(fileOperations));

    assertSame(primaryFailure, exception.getCause());
    assertFalse(fileOperations.observations.stageCreatedAndWritten);
    assertFalse(fileOperations.observations.linkAttempted);
    assertEquals(0, fileOperations.observations.directoryForceCount);
  }

  private static ArtifactPublicationResult export(RecordingFileOperations fileOperations) {
    return exporterWith(fileOperations)
        .export(
            Path.of("trial-balance.pdf"),
            TrialBalanceReportModelBuilder.buildModel(trialBalanceReport()));
  }
}
