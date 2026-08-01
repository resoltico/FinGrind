package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceRejectionJsonModels;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps machine filesystem facts typed and confines redaction to the text projection. */
class CliFailurePathContractTest {
  @Test
  void contractFailurePathsBecomeRealJsonFactsAndRedactedTextRows() {
    Path keyFile = Path.of("secrets", "entity.key");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
                keyFile,
                "The FinGrind book key file does not exist.",
                "Create the key file before retrying.",
                "--book-key-file"));

    CliEnvelopeJsonModels.Envelope<?> envelope = CliEnvelopeMapper.failureEnvelope(failure);
    String text = CliFailureOutputRenderer.renderFailureText(failure);
    String absoluteKeyFile = CliPublicPaths.absoluteValue(keyFile);

    assertEquals(absoluteKeyFile, envelope.path());
    assertEquals(List.of(), envelope.relatedPaths());
    assertFalse(java.util.Objects.requireNonNull(envelope.message()).contains(absoluteKeyFile));
    assertTrue(text.contains("Path"), text);
    assertTrue(text.contains("<redacted>"), text);
    assertFalse(text.contains(absoluteKeyFile), text);
  }

  @Test
  void failureEnvelopePublishesAbsolutePathsWhileTextRedactsThem() {
    Path primary = Path.of("reports", "trial-balance.pdf");
    Path related = Path.of("reports", "source.sqlite");
    CliFailure failure =
        new CliFailure(
            "pdf-export-failure",
            "Failed to write the PDF export.",
            "Repair the destination.",
            "--pdf-out",
            primary,
            List.of(related));

    CliEnvelopeJsonModels.Envelope<?> envelope = CliEnvelopeMapper.failureEnvelope(failure);
    String text = CliFailureOutputRenderer.renderFailureText(failure);

    assertEquals(CliPublicPaths.absoluteValue(primary), envelope.path());
    assertEquals(List.of(CliPublicPaths.absoluteValue(related)), envelope.relatedPaths());
    assertFalse(java.util.Objects.requireNonNull(envelope.message()).contains(primary.toString()));
    assertTrue(text.contains("<redacted>"), text);
    assertTrue(text.contains("Related paths"), text);
    assertFalse(text.contains(CliPublicPaths.absoluteValue(primary)), text);
    assertFalse(text.contains(CliPublicPaths.absoluteValue(related)), text);
    assertEquals(
        FailureCategory.PRECONDITION, ContractResponseCatalog.failureCategoryFor(failure.code()));
    String pathOnlyText =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "pdf-export-failure", "Failed to write the PDF export.", null, null, primary));
    assertTrue(pathOnlyText.contains("Path"), pathOnlyText);
    assertFalse(pathOnlyText.contains("Related paths"), pathOnlyText);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliFailure(
                "pdf-export-failure",
                "Failed.",
                null,
                null,
                new CliErrorJsonModels.InvalidRequestDetails(List.of("path")),
                null,
                List.of(related),
                null));
  }

  @Test
  void equivalentPairTargetsHaveNoDuplicatedRelatedPath() {
    CliEnvelopeFailurePaths paths =
        java.util.Objects.requireNonNull(
            CliEnvelopeFailurePaths.from(
                new CliMaintenanceRejectionJsonModels.PairTargetsConflictDetails(
                    "/books/live.sqlite", "/books/live.sqlite")),
            "pair conflict paths");

    assertEquals("/books/live.sqlite", paths.path());
    assertEquals(List.of(), paths.relatedPaths());
  }

  @Test
  void artifactPublicationDurabilityDetailsKeepCanonicalJsonPathsButRedactTextWithoutDuplicates() {
    Path publishedArtifact =
        Path.of("/Users/private-fixture/FinGrind/private-exports/attestation-receipt.json");
    Path residualStage =
        Path.of("/Users/private-fixture/FinGrind/private-exports/.fingrind-attestation-stage.tmp");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.artifactPublicationDurabilityUncertainFailure(
                new ArtifactPublicationResult(
                    publishedArtifact, new ArtifactPublicationRetention(residualStage)),
                "--receipt-file"));

    CliEnvelopeJsonModels.Envelope<?> envelope = CliEnvelopeMapper.failureEnvelope(failure);
    String json = CliWireJson.prettyJsonText(envelope);
    String text = CliFailureOutputRenderer.renderFailureText(failure);

    assertEquals(CliPublicPaths.absoluteValue(publishedArtifact), envelope.path());
    assertEquals(List.of(CliPublicPaths.absoluteValue(residualStage)), envelope.relatedPaths());
    assertTrue(json.contains(CliPublicPaths.absoluteValue(publishedArtifact)), json);
    assertTrue(json.contains(CliPublicPaths.absoluteValue(residualStage)), json);
    assertTrue(text.contains("Published artifact"), text);
    assertTrue(text.contains("Retained stage path"), text);
    assertTrue(text.contains("<redacted>/FinGrind/private-exports/attestation-receipt.json"), text);
    assertTrue(
        text.contains("<redacted>/FinGrind/private-exports/.fingrind-attestation-stage.tmp"), text);
    assertFalse(text.contains("/Users/private-fixture"), text);
    assertFalse(text.contains("Related paths"), text);
  }

  @Test
  void artifactPublicationOutcomeDetailsKeepCandidateAndStagePathsInJsonButRedactText() {
    Path candidateArtifact =
        Path.of("/Users/private-fixture/FinGrind/private-exports/indeterminate-receipt.json");
    Path residualStage =
        Path.of(
            "/Users/private-fixture/FinGrind/private-exports/.fingrind-indeterminate-stage.tmp");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.artifactPublicationOutcomeUncertainFailure(
                candidateArtifact,
                new ArtifactPublicationRetention(residualStage),
                "--receipt-file"));

    CliEnvelopeJsonModels.Envelope<?> envelope = CliEnvelopeMapper.failureEnvelope(failure);
    String json = CliWireJson.prettyJsonText(envelope);
    String text = CliFailureOutputRenderer.renderFailureText(failure);

    assertEquals(CliPublicPaths.absoluteValue(candidateArtifact), envelope.path());
    assertEquals(List.of(CliPublicPaths.absoluteValue(residualStage)), envelope.relatedPaths());
    assertTrue(json.contains(CliPublicPaths.absoluteValue(candidateArtifact)), json);
    assertTrue(json.contains(CliPublicPaths.absoluteValue(residualStage)), json);
    assertTrue(text.contains("Candidate artifact path"), text);
    assertTrue(text.contains("Retained stage path"), text);
    assertTrue(
        text.contains("<redacted>/FinGrind/private-exports/indeterminate-receipt.json"), text);
    assertTrue(
        text.contains("<redacted>/FinGrind/private-exports/.fingrind-indeterminate-stage.tmp"),
        text);
    assertFalse(text.contains("/Users/private-fixture"), text);
    assertFalse(text.contains("Related paths"), text);
  }

  @Test
  void cleanArtifactPublicationOutcomeOmitsTheAbsentRetainedStageFactFromJson() {
    Path candidateArtifact =
        Path.of("/Users/private-fixture/FinGrind/private-exports/clean-indeterminate-receipt.json");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.artifactPublicationOutcomeUncertainFailure(
                candidateArtifact, null, "--receipt-file"));

    String json = CliWireJson.prettyJsonText(CliEnvelopeMapper.failureEnvelope(failure));

    assertTrue(json.contains(CliPublicPaths.absoluteValue(candidateArtifact)), json);
    assertFalse(json.contains("\"retainedStage\""), json);
  }

  @Test
  void envelopePathValidation_requiresRelatedPathsAndKeepsExplicitPathsUntouched() {
    CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> failureWithExplicitPath =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.ERROR,
            null,
            "pdf-export-failure",
            "Failed to write the PDF export.",
            null,
            "--pdf-out",
            null,
            null,
            null,
            CliPublicPaths.absoluteValue(Path.of("reports", "trial-balance.pdf")),
            List.of(),
            null);
    CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload> success =
        CliEnvelopeMapper.successEnvelope(
            new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
                "FinGrind",
                "0.62.0",
                MachineContract.protocolVersion(),
                "CLI help",
                DiscoveryDetail.COMPACT,
                null,
                List.of(),
                List.of("help"),
                List.of(),
                "hint",
                null));

    assertSame(
        failureWithExplicitPath, CliEnvelopeMapper.withFailurePaths(failureWithExplicitPath));
    assertSame(success, CliEnvelopeMapper.withFailurePaths(success));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliEnvelopeJsonModels.Envelope<ProtocolSuccessPayload>(
                ProtocolEnvelopeStatus.ERROR,
                null,
                "pdf-export-failure",
                "Failed to write the PDF export.",
                null,
                "--pdf-out",
                null,
                null,
                null,
                CliPublicPaths.absoluteValue(Path.of("reports", "trial-balance.pdf")),
                null,
                null));
  }
}
