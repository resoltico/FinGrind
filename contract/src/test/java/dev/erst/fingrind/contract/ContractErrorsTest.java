package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.contract.runtime.ErrorDescriptor;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for failures that report uncertain artifact or book-opening outcomes. */
class ContractErrorsTest extends ContractTestSupport {
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @TempDir Path temporaryDirectory;

  @Test
  void artifactPublicationFailureFactories_reportEveryArtifactThatNeedsInspection() {
    Path candidateArtifact = temporaryDirectory.resolve("receipt.fgar");
    Path residualStage = temporaryDirectory.resolve(".receipt.fgar-stage");
    ArtifactPublicationRetention retainedStage = new ArtifactPublicationRetention(residualStage);

    ContractFailure retainedPrimaryFailure =
        ContractErrors.withRetainedArtifactStage(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
                candidateArtifact,
                "FinGrind could not create the receipt artifact.",
                "Choose a fresh receipt destination.",
                "--receipt-file"),
            retainedStage);
    ContractFailure outcomeWithoutStageFailure =
        ContractErrors.artifactPublicationOutcomeUncertainFailure(
            candidateArtifact, null, "--receipt-file");
    ContractFailure outcomeWithStageFailure =
        ContractErrors.artifactPublicationOutcomeUncertainFailure(
            candidateArtifact, retainedStage, "--receipt-file");
    ArtifactPublicationResult durabilityPublication =
        new ArtifactPublicationResult(candidateArtifact, retainedStage);
    ArtifactPublicationRetention secondRetainedStage =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".second-receipt-stage"));
    ArtifactPublicationResult secondDurabilityPublication =
        new ArtifactPublicationResult(
            temporaryDirectory.resolve("second-receipt.fgar"), secondRetainedStage);
    ContractFailure durabilityFailure =
        ContractErrors.artifactPublicationDurabilityUncertainFailure(
            durabilityPublication, "--receipt-file");
    ContractFailure secondDurabilityFailure =
        ContractErrors.artifactPublicationDurabilityUncertainFailure(
            secondDurabilityPublication, "--receipt-file");

    assertEquals(retainedStage, retainedPrimaryFailure.retainedStage());
    assertEquals(
        List.of(
            Objects.requireNonNull(retainedPrimaryFailure.retainedStage(), "retained-primary stage")
                .retainedStagePath()),
        Objects.requireNonNull(retainedPrimaryFailure.paths(), "retained-primary paths")
            .relatedPaths());

    var outcomeWithoutStageDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class,
            outcomeWithoutStageFailure.details());
    assertEquals(
        candidateArtifact.toAbsolutePath().normalize(),
        outcomeWithoutStageDetails.candidateArtifactPath());
    assertEquals(
        List.of(),
        Objects.requireNonNull(outcomeWithoutStageFailure.paths(), "outcome paths").relatedPaths());

    var outcomeWithStageDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class,
            outcomeWithStageFailure.details());
    assertEquals(retainedStage, outcomeWithStageDetails.retainedStage());
    assertEquals(retainedStage, outcomeWithStageFailure.retainedStage());
    assertEquals(
        List.of(residualStage.toAbsolutePath().normalize()),
        Objects.requireNonNull(outcomeWithStageFailure.paths(), "outcome paths").relatedPaths());

    var durabilityDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class,
            durabilityFailure.details());
    assertEquals(
        durabilityPublication.publishedArtifactPath(),
        durabilityDetails.publication().publishedArtifactPath());
    assertEquals(
        List.of(durabilityPublication.retention().retainedStagePath()),
        Objects.requireNonNull(durabilityFailure.paths(), "durability paths").relatedPaths());
    assertEquals(durabilityPublication.retention(), durabilityFailure.retainedStage());
    assertEquals(
        List.of(secondDurabilityPublication.retention().retainedStagePath()),
        Objects.requireNonNull(secondDurabilityFailure.paths(), "second durability paths")
            .relatedPaths());

    ContractFailure noPathFailure =
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failure("failure", null, null);
    ContractFailure retainedNoPathFailure =
        ContractErrors.withRetainedArtifactStage(noPathFailure, retainedStage);
    assertEquals(
        retainedStage.retainedStagePath(),
        Objects.requireNonNull(retainedNoPathFailure.paths(), "retained no-path failure paths")
            .path());
    ContractFailure candidatePathFailure =
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
            candidateArtifact, "failure", null, null);
    ContractFailure retainedCandidatePathFailure =
        ContractErrors.withRetainedArtifactStage(candidatePathFailure, retainedStage);
    assertEquals(
        List.of(retainedStage.retainedStagePath()),
        Objects.requireNonNull(
                ContractErrors.withRetainedArtifactStage(
                        retainedCandidatePathFailure, retainedStage)
                    .paths(),
                "repeated retained candidate failure paths")
            .relatedPaths());
    assertEquals(
        List.of(),
        Objects.requireNonNull(
                ContractErrors.withRetainedArtifactStage(
                        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
                            retainedStage.retainedStagePath(), "failure", null, null),
                        retainedStage)
                    .paths(),
                "retained primary failure paths")
            .relatedPaths());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractErrors.withRetainedArtifactStage(
                retainedCandidatePathFailure,
                new ArtifactPublicationRetention(temporaryDirectory.resolve(".other-stage"))));
  }

  @Test
  void openBookUncertaintyFailureFactories_preserveAllDistinctOperatorInspectionPaths() {
    Path bookFile = temporaryDirectory.resolve("book.fgr");
    Path bookSidecar = temporaryDirectory.resolve("book.fgr-wal");
    Path founderKeyFile = temporaryDirectory.resolve("founder-1.fgatk");
    Path founderKeyStage = temporaryDirectory.resolve(".founder-1.fgatk-stage");
    Path cleanFounderKeyFile = temporaryDirectory.resolve("founder-2.fgatk");
    ArtifactPublicationRetention uncertainFounderStage =
        new ArtifactPublicationRetention(founderKeyStage);
    ArtifactPublicationResult uncertainFounderKey =
        new ArtifactPublicationResult(founderKeyFile, uncertainFounderStage);
    ArtifactPublicationResult cleanFounderKey =
        new ArtifactPublicationResult(
            cleanFounderKeyFile,
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-2-stage")));
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact founderArtifact =
        OpenBookFailureDetails.RetainedOpenBookPreparationArtifact.founderKey(uncertainFounderKey);
    assertEquals(
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY_STAGE,
            founderKeyStage,
            uncertainFounderStage),
        OpenBookFailureDetails.retainedArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY_STAGE,
            founderKeyStage,
            uncertainFounderStage));
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact sidecarArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR, bookSidecar, null);
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact bookArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE, bookFile, null);

    ContractFailure retainedFailure =
        ContractErrors.openBookPreparationArtifactsRetainedFailure(
            List.of(founderArtifact, sidecarArtifact));
    ContractFailurePaths retainedPaths =
        Objects.requireNonNull(retainedFailure.paths(), "retained paths");
    assertEquals(founderArtifact.path(), retainedPaths.path());
    assertEquals(
        List.of(
            Objects.requireNonNull(founderArtifact.retainedStage(), "founder artifact retention")
                .retainedStagePath(),
            sidecarArtifact.path()),
        retainedPaths.relatedPaths());

    AttestationRegistryInspection trustRoot = attestationTrustRoot();
    OpenBookFailureDetails.OpenBookCompletionUncertain completionDetails =
        new OpenBookFailureDetails.OpenBookCompletionUncertain(
            bookFile,
            Instant.parse("2026-07-26T12:00:00Z"),
            bookIdentity(),
            trustRoot,
            new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex()),
            List.of(
                ContractPublicationTransactionFixtures.completedArtifact(
                    uncertainFounderKey.publishedArtifactPath()),
                ContractPublicationTransactionFixtures.completedArtifact(
                    cleanFounderKey.publishedArtifactPath())),
            List.of(bookArtifact, sidecarArtifact));

    ContractFailure completionFailure =
        ContractErrors.openBookCompletionUncertainFailure(completionDetails);
    ContractFailurePaths completionPaths =
        Objects.requireNonNull(completionFailure.paths(), "completion paths");
    assertEquals(bookFile.toAbsolutePath().normalize(), completionPaths.path());
    assertEquals(
        List.of(
            sidecarArtifact.path(),
            uncertainFounderKey.publishedArtifactPath(),
            cleanFounderKey.publishedArtifactPath()),
        completionPaths.relatedPaths());
    assertEquals(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN, completionFailure.descriptor());
  }

  @Test
  void openBookPublicationProgressFactory_reportsOnlyFinalArtifactsAndTransactionFacts() {
    Path completedFounderKey = temporaryDirectory.resolve("completed-founder.fgatk");
    Path incompleteFounderKey = temporaryDirectory.resolve("incomplete-founder.fgatk");
    var completedPublication =
        ContractPublicationTransactionFixtures.completedArtifact(completedFounderKey);
    ContractFailureDetails.PublicationTransactionIncomplete incompletePublication =
        new ContractFailureDetails.PublicationTransactionIncomplete(
            incompleteFounderKey, ContractPublicationTransactionFixtures.incompleteResult());

    ContractFailure failure =
        ContractErrors.openBookPublicationProgressFailure(
            List.of(completedPublication), incompletePublication);

    assertEquals(ContractErrors.Descriptor.OPEN_BOOK_PUBLICATION_PROGRESS, failure.descriptor());
    ContractFailurePaths paths = Objects.requireNonNull(failure.paths(), "publication paths");
    assertEquals(completedFounderKey.toAbsolutePath().normalize(), paths.path());
    assertEquals(List.of(incompleteFounderKey.toAbsolutePath().normalize()), paths.relatedPaths());
    OpenBookFailureDetails.OpenBookPublicationProgress details =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookPublicationProgress.class, failure.details());
    assertEquals(incompletePublication, details.incompleteFounderKeyPublication());

    ContractFailure completedOnlyFailure =
        ContractErrors.openBookPublicationProgressFailure(List.of(completedPublication), null);
    assertEquals(
        List.of(),
        Objects.requireNonNull(completedOnlyFailure.paths(), "completed-only paths")
            .relatedPaths());

    ContractFailure incompleteFailure =
        ContractErrors.publicationTransactionIncompleteFailure(
            incompleteFounderKey,
            ContractPublicationTransactionFixtures.incompleteResult(),
            "--attestation-key-file");
    assertEquals(
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
        incompleteFailure.descriptor());
    assertEquals(
        incompleteFounderKey.toAbsolutePath().normalize(),
        Objects.requireNonNull(incompleteFailure.paths(), "incomplete transaction paths").path());
  }

  @Test
  void protectedBookPairEvidenceBlockedFailure_exposesOnlyTheTwoFinalMembers() {
    Path bookTarget = temporaryDirectory.resolve("protected-book.sqlite");
    Path secretTarget = temporaryDirectory.resolve("protected-book.key");
    ContractFailureDetails.PairPublication pairPublication =
        new ContractFailureDetails.PairPublication(
            new ContractFailureDetails.PairPublicationMember(
                bookTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
            new ContractFailureDetails.PairPublicationMember(
                secretTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED));

    ContractFailure failure =
        ContractErrors.protectedBookPairPublicationEvidenceBlockedFailure(pairPublication);

    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        failure.descriptor());
    assertEquals(
        bookTarget.toAbsolutePath().normalize(),
        Objects.requireNonNull(failure.paths(), "blocked pair paths").path());
    assertEquals(
        List.of(secretTarget.toAbsolutePath().normalize()),
        Objects.requireNonNull(failure.paths(), "blocked pair paths").relatedPaths());
    assertEquals(
        pairPublication,
        assertInstanceOf(
                ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked.class,
                failure.details())
            .pairPublication());
  }

  @Test
  void descriptorEnumeration_exposesEveryDeclaredErrorAndItsStructuredFields() {
    List<ErrorDescriptor> descriptors = ContractErrors.descriptors();

    assertEquals(ContractErrors.Descriptor.values().length, descriptors.size());
    assertEquals(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN.category(),
        descriptors.stream()
            .filter(descriptor -> "open-book-completion-uncertain".equals(descriptor.code()))
            .findFirst()
            .orElseThrow()
            .category());
    assertTrue(
        descriptors.stream()
            .anyMatch(
                descriptor ->
                    "open-book-completion-uncertain".equals(descriptor.code())
                        && descriptor.detailFields().size() == 7));
    assertTrue(
        descriptors.stream()
            .anyMatch(
                descriptor ->
                    "artifact-publication-outcome-uncertain".equals(descriptor.code())
                        && descriptor.detailFields().size() == 2));
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
