package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Validates the mutually exclusive legacy-stage and transaction publication evidence forms. */
class CliPublicationTransactionJsonModelTest {
  private static final Path OUTPUT_ROOT =
      Path.of("/Users/private-fixture/FinGrind/publication-json-models");

  @Test
  void successArtifactsKeepExactlyOneEvidenceFormAcrossBothPublisherGenerations() {
    CliDiscoveryCommonJsonModels.CommandCountPayload payload =
        new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1);
    Path legacyPath = OUTPUT_ROOT.resolve("legacy.pdf");
    ArtifactPublicationResult legacyPublication =
        new ArtifactPublicationResult(
            legacyPath, new ArtifactPublicationRetention(OUTPUT_ROOT.resolve(".legacy.pdf-stage")));

    assertNull(
        CliEnvelopeMapper.successEnvelope(payload, (ArtifactPublicationResult) null).artifacts());
    CliEnvelopeJsonModels.SuccessArtifact legacy =
        Objects.requireNonNull(
                CliEnvelopeMapper.successEnvelope(payload, legacyPublication).artifacts(),
                "legacy artifacts")
            .getFirst();
    CliEnvelopeJsonModels.SuccessArtifact transaction =
        new CliEnvelopeJsonModels.SuccessArtifact(
            "pdf", OUTPUT_ROOT.resolve("current.pdf").toString(), transaction());

    assertEquals(CliPublicPaths.absoluteValue(legacyPath), legacy.path());
    assertEquals(
        CliPublicPaths.absoluteValue(OUTPUT_ROOT.resolve(".legacy.pdf-stage")),
        legacy.retainedStage());
    assertNull(transaction.retainedStage());
    assertEquals(
        "transaction-1",
        Objects.requireNonNull(transaction.publicationTransaction(), "publication transaction")
            .id());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliEnvelopeJsonModels.SuccessArtifact("pdf", "/same", "/same"));
  }

  @Test
  void publicationProgressDetailsRequireNonduplicatedRecoveryFacts() {
    CliEnvelopeJsonModels.SuccessArtifact completed =
        new CliEnvelopeJsonModels.SuccessArtifact(
            "attestation-key", "/founders/one.fgatk", transaction());
    CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails incomplete =
        new CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails(
            "/founders/two.fgatk", transaction());

    CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails details =
        new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(
            List.of(completed), incomplete);
    assertEquals(List.of(completed), details.publishedFounderKeyArtifacts());
    assertEquals(incomplete, details.incompleteFounderKeyPublication());
    assertEquals(
        List.of(completed),
        new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(List.of(completed), null)
            .publishedFounderKeyArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(
                List.of(completed, completed), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(
                List.of(completed),
                new CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails(
                    completed.path(), transaction())));
  }

  @Test
  void legacyFounderKeyStagesRemainVisibleInTheTextRecoveryProjection() {
    Path founderKey = OUTPUT_ROOT.resolve("founders/operator.fgatk");
    Path retainedStage = OUTPUT_ROOT.resolve("founders/.operator.fgatk-stage");
    List<List<String>> rows = new ArrayList<>();

    CliOpenBookErrorDetailsTextRenderer.appendPublicationProgressRows(
        rows,
        new CliOpenBookErrorJsonModels.OpenBookPublicationProgressDetails(
            List.of(
                new CliEnvelopeJsonModels.SuccessArtifact(
                    "attestation-key", founderKey.toString(), retainedStage.toString())),
            null));

    assertTrue(
        rows.contains(
            List.of(
                "Founder-key retained stage",
                CliTextDisplay.serializedAbsolutePath(
                    CliPublicPaths.absoluteValue(retainedStage)))));
  }

  private static CliEnvelopeJsonModels.PublicationTransaction transaction() {
    return new CliEnvelopeJsonModels.PublicationTransaction(
        "transaction-1", "blocked", "none-committed", "incomplete");
  }
}
