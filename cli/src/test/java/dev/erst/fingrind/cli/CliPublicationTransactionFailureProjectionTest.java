package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PublicationTransactionFinalTargetOccupiedException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves journal-only publication facts remain complete across CLI error projections. */
class CliPublicationTransactionFailureProjectionTest {
  private static final Path OUTPUT_ROOT =
      Path.of("/Users/private-fixture/FinGrind/publication-transactions");

  @Test
  void incompleteTransactionProjectsIdOnlyRecoveryFactsToJsonAndText() {
    Path candidate = OUTPUT_ROOT.resolve("receipt.fgar");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.publicationTransactionIncompleteFailure(
                candidate,
                CliPublicationTransactionTestFixtures.incompleteResult(),
                "--receipt-file"));

    CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails details =
        assertInstanceOf(
            CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails.class,
            failure.details());
    String json = CliWireJson.prettyJsonText(CliEnvelopeMapper.failureEnvelope(failure));
    String text = CliFailureOutputRenderer.renderFailureText(failure);

    assertEquals(CliPublicPaths.absoluteValue(candidate), details.candidateArtifact());
    assertEquals("blocked", details.publicationTransaction().state());
    assertTrue(json.contains("\"publicationTransaction\""), json);
    assertTrue(text.contains("Publication transaction"), text);
    assertTrue(text.contains("Publication cleanup outcome"), text);
    assertTrue(text.contains("<redacted>/FinGrind/publication-transactions/receipt.fgar"), text);
    assertFalse(text.contains(OUTPUT_ROOT.toString()), text);
  }

  @Test
  void openBookProgressProjectsCompletedAndIncompleteFounderTransactions() {
    Path publishedFounder = OUTPUT_ROOT.resolve("founder-complete.fgatk");
    Path incompleteFounder = OUTPUT_ROOT.resolve("founder-incomplete.fgatk");
    ContractFailureDetails.PublicationTransactionIncomplete incomplete =
        new ContractFailureDetails.PublicationTransactionIncomplete(
            incompleteFounder, CliPublicationTransactionTestFixtures.incompleteResult());
    CliFailure progress =
        CliFailure.fromContractFailure(
            ContractErrors.openBookPublicationProgressFailure(
                List.of(CliPublicationTransactionTestFixtures.completedArtifact(publishedFounder)),
                incomplete));
    CliFailure completedOnly =
        CliFailure.fromContractFailure(
            ContractErrors.openBookPublicationProgressFailure(
                List.of(CliPublicationTransactionTestFixtures.completedArtifact(publishedFounder)),
                null));

    CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails details =
        assertInstanceOf(
            CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails.class,
            progress.details());
    String progressText = CliFailureOutputRenderer.renderFailureText(progress);
    String completedOnlyText = CliFailureOutputRenderer.renderFailureText(completedOnly);

    assertEquals(1, details.publishedFounderKeyArtifacts().size());
    assertNotNull(details.incompleteFounderKeyPublication());
    assertTrue(progressText.contains("New founder key file"), progressText);
    assertTrue(progressText.contains("Founder-key publication transaction"), progressText);
    assertTrue(progressText.contains("Candidate artifact path"), progressText);
    assertFalse(progressText.contains(OUTPUT_ROOT.toString()), progressText);
    assertFalse(completedOnlyText.contains("Candidate artifact path"), completedOnlyText);
  }

  @Test
  void legacyStageFactsRemainExplicitUntilTheirProducerMigrates() {
    Path candidate = OUTPUT_ROOT.resolve("legacy.pdf");
    Path retainedStage = OUTPUT_ROOT.resolve(".legacy.pdf-stage");
    CliFailure retainedFailure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                candidate,
                new ArtifactPublicationRetainedStageException(
                    new ArtifactPublicationRetention(retainedStage),
                    new IOException("write failed"))));
    CliFailure outcomeFailure =
        CliFailure.fromContractFailure(
            ContractErrors.artifactPublicationOutcomeUncertainFailure(
                candidate, new ArtifactPublicationRetention(retainedStage), "--pdf-out"));

    assertNotNull(retainedFailure);
    assertEquals(retainedStage.toAbsolutePath().normalize(), retainedFailure.retainedStage());
    assertInstanceOf(
        CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails.class,
        outcomeFailure.details());
    assertTrue(
        CliFailureOutputRenderer.renderFailureText(outcomeFailure).contains("Retained stage path"));
  }

  @Test
  void retainedLatePdfTargetCollisionPreservesTheNoClobberContractAndRecoveryStage() {
    Path candidate = OUTPUT_ROOT.resolve("late-collision.pdf");
    Path retainedStage = OUTPUT_ROOT.resolve(".late-collision.pdf-stage");
    CliFailure failure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                candidate,
                new ArtifactPublicationRetainedStageException(
                    new ArtifactPublicationRetention(retainedStage),
                    new PublicationTransactionFinalTargetOccupiedException(
                        candidate, new FileAlreadyExistsException(candidate.toString())))));

    assertNotNull(failure);
    assertEquals("artifact-output-already-exists", failure.code());
    assertEquals(candidate.toAbsolutePath().normalize(), failure.path());
    assertEquals(retainedStage.toAbsolutePath().normalize(), failure.retainedStage());
    assertEquals("--pdf-out", failure.argument());
  }
}
