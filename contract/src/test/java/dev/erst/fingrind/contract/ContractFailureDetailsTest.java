package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for the structured facts retained when artifact or book opening is uncertain. */
class ContractFailureDetailsTest extends ContractTestSupport {
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @TempDir Path temporaryDirectory;

  @Test
  void artifactPublicationDetails_preserveEveryIndependentRetainedStageFact() {
    Path candidateArtifact = temporaryDirectory.resolve("receipt.fgar");
    Path residualStage = temporaryDirectory.resolve(".receipt.fgar-stage");
    ContractFailureDetails.ArtifactPublicationOutcomeUncertain outcomeWithoutStage =
        new ContractFailureDetails.ArtifactPublicationOutcomeUncertain(candidateArtifact, null);
    ArtifactPublicationRetention retainedStage = new ArtifactPublicationRetention(residualStage);
    ArtifactPublicationResult firstPublication =
        new ArtifactPublicationResult(candidateArtifact, retainedStage);
    ArtifactPublicationResult secondPublication =
        new ArtifactPublicationResult(
            candidateArtifact,
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".receipt-2-stage")));

    assertEquals(
        candidateArtifact.toAbsolutePath().normalize(),
        outcomeWithoutStage.candidateArtifactPath());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.ArtifactPublicationOutcomeUncertain(
                candidateArtifact, new ArtifactPublicationRetention(candidateArtifact)));
    assertEquals(
        firstPublication,
        new ContractFailureDetails.ArtifactPublicationDurabilityUncertain(firstPublication)
            .publication());
    ContractFailureDetails.ArtifactPublicationDurabilityUncertain durabilityUncertain =
        new ContractFailureDetails.ArtifactPublicationDurabilityUncertain(secondPublication);
    assertEquals(secondPublication, durabilityUncertain.publication());
    assertThrows(
        NullPointerException.class,
        () -> new ContractFailureDetails.ArtifactPublicationDurabilityUncertain(nullOf()));
  }

  @Test
  void pairPublicationDetails_bindEveryRetainedStageToItsReportedFinalMember() {
    Path bookTarget = temporaryDirectory.resolve("recovered.sqlite");
    Path secretTarget = temporaryDirectory.resolve("recovered.book-key");
    ProtectedBookPairPublicationRetention retention =
        new ProtectedBookPairPublicationRetention(
            new ArtifactPublicationResult(
                bookTarget,
                new ArtifactPublicationRetention(
                    temporaryDirectory.resolve(".recovered-book-stage"))),
            new ArtifactPublicationResult(
                secretTarget,
                new ArtifactPublicationRetention(
                    temporaryDirectory.resolve(".recovered-secret-stage"))));

    ContractFailureDetails.PairPublication details =
        new ContractFailureDetails.PairPublication(
            new ContractFailureDetails.PairPublicationMember(
                bookTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
            new ContractFailureDetails.PairPublicationMember(
                secretTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
            dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
                .DURABLY_RETAINED,
            retention);

    assertEquals(retention, details.pairPublicationRetention());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.PairPublication(
                new ContractFailureDetails.PairPublicationMember(
                    bookTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
                new ContractFailureDetails.PairPublicationMember(
                    secretTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
                dev.erst.fingrind.contract.bookkeeping
                    .ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.PairPublication(
                new ContractFailureDetails.PairPublicationMember(
                    temporaryDirectory.resolve("other.sqlite"),
                    ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
                new ContractFailureDetails.PairPublicationMember(
                    secretTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
                dev.erst.fingrind.contract.bookkeeping
                    .ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                retention));

    ContractFailureDetails.PairPublication evidenceBlocked =
        new ContractFailureDetails.PairPublication(
            new ContractFailureDetails.PairPublicationMember(
                bookTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
            new ContractFailureDetails.PairPublicationMember(
                secretTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
            null,
            null);
    assertNull(evidenceBlocked.pairPublicationRetention());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.PairPublication(
                new ContractFailureDetails.PairPublicationMember(
                    bookTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
                new ContractFailureDetails.PairPublicationMember(
                    secretTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
                null,
                retention));
  }

  @Test
  void openBookRetentionDetails_preserveDistinctCanonicalArtifactsAndRejectAmbiguity() {
    Path founderKeyFile = temporaryDirectory.resolve("founder.fgatk");
    Path residualStage = temporaryDirectory.resolve(".founder.fgatk-stage");
    ArtifactPublicationResult founderPublication =
        new ArtifactPublicationResult(
            founderKeyFile, new ArtifactPublicationRetention(residualStage));
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact founderArtifact =
        OpenBookFailureDetails.RetainedOpenBookPreparationArtifact.founderKey(founderPublication);
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact bookArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
            temporaryDirectory.resolve("book.fgr"),
            null);
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> mutableArtifacts =
        new ArrayList<>(List.of(founderArtifact, bookArtifact));

    OpenBookFailureDetails.OpenBookPreparationArtifactsRetained details =
        new OpenBookFailureDetails.OpenBookPreparationArtifactsRetained(mutableArtifacts);
    mutableArtifacts.clear();

    assertEquals(List.of(founderArtifact, bookArtifact), details.retainedArtifacts());
    assertEquals(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
        founderArtifact.role());
    assertEquals(founderPublication.publishedArtifactPath(), founderArtifact.path());
    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenBookFailureDetails.OpenBookPreparationArtifactsRetained(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenBookFailureDetails.OpenBookPreparationArtifactsRetained(
                List.of(
                    bookArtifact,
                    new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
                        temporaryDirectory.resolve("book.fgr"),
                        null))));
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> artifactsWithNull =
        new ArrayList<>();
    artifactsWithNull.add(nullOf());
    assertThrows(
        NullPointerException.class,
        () -> new OpenBookFailureDetails.OpenBookPreparationArtifactsRetained(artifactsWithNull));
  }

  @Test
  void openBookPublicationProgress_preservesOnlyTransactionOwnedFinalFacts() {
    Path completedFounderKey = temporaryDirectory.resolve("completed-founder.fgatk");
    Path incompleteFounderKey = temporaryDirectory.resolve("incomplete-founder.fgatk");
    dev.erst.fingrind.core.PublicationTransactionArtifact completedPublication =
        ContractPublicationTransactionFixtures.completedArtifact(completedFounderKey);
    ContractFailureDetails.PublicationTransactionIncomplete incompletePublication =
        new ContractFailureDetails.PublicationTransactionIncomplete(
            incompleteFounderKey, ContractPublicationTransactionFixtures.incompleteResult());
    List<dev.erst.fingrind.core.PublicationTransactionArtifact> mutableCompletedPublications =
        new ArrayList<>(List.of(completedPublication));

    OpenBookFailureDetails.OpenBookPublicationProgress progress =
        new OpenBookFailureDetails.OpenBookPublicationProgress(
            mutableCompletedPublications, incompletePublication);
    mutableCompletedPublications.clear();

    assertEquals(List.of(completedPublication), progress.publishedFounderKeyArtifacts());
    assertEquals(incompletePublication, progress.incompleteFounderKeyPublication());
    assertEquals(
        List.of(completedPublication),
        new OpenBookFailureDetails.OpenBookPublicationProgress(List.of(completedPublication), null)
            .publishedFounderKeyArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () -> new OpenBookFailureDetails.OpenBookPublicationProgress(List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenBookFailureDetails.OpenBookPublicationProgress(
                List.of(completedPublication, completedPublication), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenBookFailureDetails.OpenBookPublicationProgress(
                List.of(completedPublication),
                new ContractFailureDetails.PublicationTransactionIncomplete(
                    completedFounderKey,
                    ContractPublicationTransactionFixtures.incompleteResult())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailureDetails.PublicationTransactionIncomplete(
                incompleteFounderKey, completedPublication.transactionResult()));
  }

  @Test
  void openBookCompletionDetails_requireOneMatchingBookFactAndUniquePublishedFounderKeys() {
    Path bookFile = temporaryDirectory.resolve("book.fgr");
    Path sidecar = temporaryDirectory.resolve("book.fgr-wal");
    dev.erst.fingrind.core.PublicationTransactionArtifact founderKey =
        ContractPublicationTransactionFixtures.completedArtifact(
            temporaryDirectory.resolve("founder.fgatk"));
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact bookArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE, bookFile, null);
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact sidecarArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR, sidecar, null);
    AttestationRegistryInspection trustRoot = attestationTrustRoot();
    AttestationCommit matchingCommit =
        new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex());
    List<dev.erst.fingrind.core.PublicationTransactionArtifact> mutableFounderKeys =
        new ArrayList<>(List.of(founderKey));
    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> mutableRetainedArtifacts =
        new ArrayList<>(List.of(bookArtifact, sidecarArtifact));

    OpenBookFailureDetails.OpenBookCompletionUncertain details =
        completionDetails(
            bookFile, trustRoot, matchingCommit, mutableFounderKeys, mutableRetainedArtifacts);
    mutableFounderKeys.clear();
    mutableRetainedArtifacts.clear();

    assertEquals(bookFile.toAbsolutePath().normalize(), details.bookFilePath());
    assertEquals(List.of(founderKey), details.publishedFounderKeyArtifacts());
    assertEquals(List.of(bookArtifact, sidecarArtifact), details.retainedBookArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(bookFile, trustRoot, matchingCommit, List.of(founderKey), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                matchingCommit,
                List.of(founderKey),
                List.of(bookArtifact, bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                matchingCommit,
                List.of(founderKey),
                List.of(sidecarArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                matchingCommit,
                List.of(founderKey),
                List.of(
                    new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
                        OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
                        temporaryDirectory.resolve("different-book.fgr"),
                        null))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                new AttestationCommit(BigInteger.ONE, trustRoot.operationHeadHex()),
                List.of(founderKey),
                List.of(bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                new AttestationCommit(trustRoot.headOrder(), "f".repeat(64)),
                List.of(founderKey),
                List.of(bookArtifact)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            completionDetails(
                bookFile,
                trustRoot,
                matchingCommit,
                List.of(founderKey, founderKey),
                List.of(bookArtifact)));
  }

  private OpenBookFailureDetails.OpenBookCompletionUncertain completionDetails(
      Path bookFile,
      AttestationRegistryInspection trustRoot,
      AttestationCommit attestationCommit,
      List<dev.erst.fingrind.core.PublicationTransactionArtifact> founderKeys,
      List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedBookArtifacts) {
    return new OpenBookFailureDetails.OpenBookCompletionUncertain(
        bookFile,
        Instant.parse("2026-07-26T12:00:00Z"),
        bookIdentity(),
        trustRoot,
        attestationCommit,
        founderKeys,
        retainedBookArtifacts);
  }

  private static AttestationRegistryInspection attestationTrustRoot() {
    return new AttestationRegistryInspection(
        UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
        BigInteger.ZERO,
        OPERATION_HEAD,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
