package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Verifies that retained book-opening facts remain complete across CLI error projections. */
class CliOpenBookFailureProjectionTest extends CliBookWorkflowFixtureSupport {
  private static final Path OPENING_ROOT = Path.of("/Users/private-fixture/FinGrind/opening");
  private static final Path BOOK_FILE = OPENING_ROOT.resolve("book.fgr");
  private static final String OPERATION_HEAD = "a".repeat(64);
  private static final UUID BOOK_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final UUID PRINCIPAL_ID = UUID.fromString("20314253-6475-7689-9a0b-bcddceeff001");

  @Test
  void preparationRetention_projectsEveryRetainedArtifactWithoutLeakingPrivatePaths() {
    Path founderKey = OPENING_ROOT.resolve("founder.fgatk");
    Path residualStage = OPENING_ROOT.resolve(".fingrind-founder-stage.tmp");
    CliFailure failure =
        CliFailure.fromContractFailure(
            ContractErrors.openBookPreparationArtifactsRetainedFailure(
                List.of(
                    retainedArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole
                            .ATTESTATION_FOUNDER_KEY,
                        founderKey,
                        new ArtifactPublicationRetention(residualStage)),
                    retainedArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
                        BOOK_FILE,
                        null))));

    CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails details =
        assertInstanceOf(
            CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails.class,
            failure.details());
    String json = CliWireJson.prettyJsonText(CliEnvelopeMapper.failureEnvelope(failure));
    String text = CliFailureOutputRenderer.renderFailureText(failure);

    assertEquals(2, details.retainedArtifacts().size());
    assertTrue(json.contains(CliPublicPaths.absoluteValue(founderKey)), json);
    assertTrue(json.contains(CliPublicPaths.absoluteValue(residualStage)), json);
    assertTrue(json.contains(CliPublicPaths.absoluteValue(BOOK_FILE)), json);
    assertTrue(text.contains("Retained open-book artifact role"), text);
    assertTrue(text.contains("attestation-founder-key"), text);
    assertTrue(text.contains("book-file"), text);
    assertTrue(text.contains("Retained stage path"), text);
    assertTrue(text.contains("<redacted>/FinGrind/opening/founder.fgatk"), text);
    assertTrue(text.contains("<redacted>/FinGrind/opening/book.fgr"), text);
    assertFalse(text.contains(OPENING_ROOT.toString()), text);
    assertFalse(text.contains("Related paths"), text);
  }

  @Test
  void completionUncertainty_projectsReportedFactsAndRendersPopulatedAndEmptyTrustRoots() {
    AttestationRegistryInspection populatedTrustRoot = populatedTrustRoot();
    CliFailure populatedFailure =
        completionFailure(
            tradingBookIdentity(),
            populatedTrustRoot,
            List.of(
                new ArtifactPublicationResult(
                    OPENING_ROOT.resolve("founder-one.fgatk"),
                    new ArtifactPublicationRetention(
                        OPENING_ROOT.resolve(".fingrind-founder-one-stage.tmp"))),
                new ArtifactPublicationResult(
                    OPENING_ROOT.resolve("founder-two.fgatk"),
                    new ArtifactPublicationRetention(
                        OPENING_ROOT.resolve(".fingrind-founder-two-stage.tmp")))));
    CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails populatedDetails =
        assertInstanceOf(
            CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails.class,
            populatedFailure.details());
    String populatedJson =
        CliWireJson.prettyJsonText(CliEnvelopeMapper.failureEnvelope(populatedFailure));
    String populatedText = CliFailureOutputRenderer.renderFailureText(populatedFailure);

    assertEquals(CliPublicPaths.absoluteValue(BOOK_FILE), populatedDetails.bookFile());
    assertEquals(populatedTrustRoot.bookId().toString(), populatedDetails.attestationBookId());
    assertTrue(
        populatedJson.contains(
            CliPublicPaths.absoluteValue(OPENING_ROOT.resolve("founder-one.fgatk"))),
        populatedJson);
    assertTrue(populatedText.contains("Reported inventory costing"), populatedText);
    assertTrue(populatedText.contains(PRINCIPAL_ID.toString()), populatedText);
    assertTrue(populatedText.contains("keyId=" + "b".repeat(64)), populatedText);
    assertTrue(populatedText.contains("post; quorum=1"), populatedText);
    assertTrue(populatedText.contains("New founder key file"), populatedText);
    assertTrue(populatedText.contains("Founder-key retained stage"), populatedText);
    assertTrue(populatedText.contains("Retained book artifact role"), populatedText);
    assertTrue(populatedText.contains("Attestation order"), populatedText);
    assertTrue(populatedText.contains("Attestation head"), populatedText);
    assertFalse(populatedText.contains(OPENING_ROOT.toString()), populatedText);
    assertFalse(populatedText.contains("Related paths"), populatedText);

    String emptyTrustRootText =
        CliFailureOutputRenderer.renderFailureText(
            completionFailure(bookIdentity(), emptyTrustRoot(), List.of()));

    assertFalse(emptyTrustRootText.contains("Reported inventory costing"), emptyTrustRootText);
    assertEquals(2, occurrences(emptyTrustRootText, "(none)"), emptyTrustRootText);
    assertFalse(emptyTrustRootText.contains("New founder key file"), emptyTrustRootText);
  }

  private static CliFailure completionFailure(
      BookIdentity identity,
      AttestationRegistryInspection trustRoot,
      List<ArtifactPublicationResult> founderKeys) {
    AttestationCommit attestationCommit =
        new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex());
    return CliFailure.fromContractFailure(
        ContractErrors.openBookCompletionUncertainFailure(
            new OpenBookFailureDetails.OpenBookCompletionUncertain(
                BOOK_FILE,
                Instant.parse("2026-07-26T12:00:00Z"),
                identity,
                trustRoot,
                attestationCommit,
                founderKeys,
                List.of(
                    retainedArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
                        BOOK_FILE,
                        null),
                    retainedArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
                        OPENING_ROOT.resolve("book.fgr-wal"),
                        new ArtifactPublicationRetention(
                            OPENING_ROOT.resolve(".fingrind-book-wal-stage.tmp")))))));
  }

  private static AttestationRegistryInspection populatedTrustRoot() {
    return new AttestationRegistryInspection(
        BOOK_ID,
        BigInteger.ZERO,
        OPERATION_HEAD,
        List.of(
            new AttestationRegistryInspection.Credential(
                PRINCIPAL_ID,
                "b".repeat(64),
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
                "operator",
                "enrolled",
                BigInteger.ZERO,
                null,
                "active")),
        List.of(new AttestationRegistryInspection.CapabilityPolicy("post", 1, 1, 1, 0)),
        List.of(),
        List.of());
  }

  private static AttestationRegistryInspection emptyTrustRoot() {
    return new AttestationRegistryInspection(
        BOOK_ID, BigInteger.ZERO, OPERATION_HEAD, List.of(), List.of(), List.of(), List.of());
  }

  private static OpenBookFailureDetails.RetainedOpenBookPreparationArtifact retainedArtifact(
      OpenBookFailureDetails.OpenBookPreparationArtifactRole role,
      Path path,
      @Nullable ArtifactPublicationRetention retainedStage) {
    return new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
        role, path, retainedStage);
  }

  private static int occurrences(String text, String fragment) {
    return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
  }
}
