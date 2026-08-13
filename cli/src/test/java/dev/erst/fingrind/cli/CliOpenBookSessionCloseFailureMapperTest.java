package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers every reconciliation shape produced when a new-book SQLite session cannot close. */
class CliOpenBookSessionCloseFailureMapperTest extends CliBookWorkflowFixtureSupport {
  @Test
  void rejectedSessionCloseFailure_preservesEveryKnownRetainedPreparationArtifactShape() {
    Path bookFile = tempDirectory.resolve("new-book.sqlite");
    CliOpenBookSessionCloseFailureMapper mapper =
        new CliOpenBookSessionCloseFailureMapper(bookFile);
    Path founderKey = tempDirectory.resolve("founder.fgatk");
    Path residualStage = tempDirectory.resolve(".founder.fgatk-stage");
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(residualStage);
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact retainedArtifact =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
            founderKey,
            retention);

    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts =
        retainedArtifacts(
            mapper.rejectedSessionCloseFailure(
                ContractErrors.openBookPreparationArtifactsRetainedFailure(
                    List.of(retainedArtifact)),
                new IllegalStateException("close failed")));
    assertEquals(retainedArtifact, retainedArtifacts.getFirst());
    assertBookArtifacts(bookFile, retainedArtifacts.subList(1, retainedArtifacts.size()));

    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> stageArtifacts =
        retainedArtifacts(
            mapper.rejectedSessionCloseFailure(
                ContractErrors.withRetainedArtifactStage(
                    ContractErrors.protectedBookVerificationFailure(), retention),
                new IllegalStateException("close failed")));
    assertEquals(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY_STAGE,
        stageArtifacts.getFirst().role());
    assertEquals(residualStage.toAbsolutePath().normalize(), stageArtifacts.getFirst().path());
    assertBookArtifacts(bookFile, stageArtifacts.subList(1, stageArtifacts.size()));

    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> outcomeArtifacts =
        retainedArtifacts(
            mapper.rejectedSessionCloseFailure(
                ContractErrors.artifactPublicationOutcomeUncertainFailure(
                    founderKey, retention, "--founder-key-file"),
                new IllegalStateException("close failed")));
    assertEquals(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
        outcomeArtifacts.getFirst().role());
    assertEquals(founderKey.toAbsolutePath().normalize(), outcomeArtifacts.getFirst().path());
    assertBookArtifacts(bookFile, outcomeArtifacts.subList(1, outcomeArtifacts.size()));

    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> ordinaryArtifacts =
        retainedArtifacts(
            mapper.rejectedSessionCloseFailure(
                ContractErrors.protectedBookVerificationFailure(),
                new IllegalStateException("close failed")));
    assertBookArtifacts(bookFile, ordinaryArtifacts);
  }

  @Test
  void workAndSessionCloseFailure_reportsEveryPossibleNewBookArtifact() {
    Path bookFile = tempDirectory.resolve("new-book.sqlite");
    CliOpenBookSessionCloseFailureMapper mapper =
        new CliOpenBookSessionCloseFailureMapper(bookFile);

    List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> artifacts =
        retainedArtifacts(
            mapper.workAndSessionCloseFailure(
                new IllegalStateException("work failed"),
                new IllegalStateException("close failed")));

    assertBookArtifacts(bookFile, artifacts);
  }

  @Test
  void acceptedSessionCloseFailure_distinguishesReturnedOpeningFactsFromReturnedRejection() {
    Path bookFile = tempDirectory.resolve("new-book.sqlite");
    CliOpenBookSessionCloseFailureMapper mapper =
        new CliOpenBookSessionCloseFailureMapper(bookFile);
    var founderPublication =
        CliPublicationTransactionTestFixtures.completedArtifact(
            tempDirectory.resolve("founder.fgatk"));
    OpenBookResult.Opened ordinaryOpened = openedBookResult(Instant.parse("2026-04-07T10:15:30Z"));
    OpenBookResult.Opened opened =
        new OpenBookResult.Opened(
            ordinaryOpened.initializedAt(),
            ordinaryOpened.bookIdentity(),
            ordinaryOpened.attestationTrustRoot(),
            ordinaryOpened.attestationCommit(),
            List.of(founderPublication));

    ContractFailure completionFailure =
        rejectedFailure(
            mapper.acceptedSessionCloseFailure(opened, new IllegalStateException("close failed")));
    assertEquals(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN.code(), completionFailure.code());
    OpenBookFailureDetails.OpenBookCompletionUncertain completion =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookCompletionUncertain.class, completionFailure.details());
    assertEquals(List.of(founderPublication), completion.publishedFounderKeyArtifacts());
    assertBookArtifacts(bookFile, completion.retainedBookArtifacts());

    ContractFailure rejectionFailure =
        rejectedFailure(
            mapper.acceptedSessionCloseFailure(
                new OpenBookResult.Rejected(
                    new BookAdministrationRejection.BookAlreadyInitialized()),
                new IllegalStateException("close failed")));
    assertEquals(
        ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED.code(),
        rejectionFailure.code());
    assertBookArtifacts(bookFile, retainedArtifacts(rejectionFailure));
  }

  private static ContractFailure rejectedFailure(ContractDecision<OpenBookResult> decision) {
    return assertInstanceOf(ContractDecision.Rejected.class, decision).failure();
  }

  private static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts(
      ContractDecision<OpenBookResult> decision) {
    return retainedArtifacts(rejectedFailure(decision));
  }

  private static List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedArtifacts(
      ContractFailure failure) {
    return assertInstanceOf(
            OpenBookFailureDetails.OpenBookPreparationArtifactsRetained.class, failure.details())
        .retainedArtifacts();
  }

  private static void assertBookArtifacts(
      Path bookFile, List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> artifacts) {
    Path normalizedBookFile = bookFile.toAbsolutePath().normalize();
    String fileName = normalizedBookFile.getFileName().toString();
    assertEquals(
        List.of(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_FILE,
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR,
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.BOOK_SIDECAR),
        artifacts.stream()
            .map(OpenBookFailureDetails.RetainedOpenBookPreparationArtifact::role)
            .toList());
    assertEquals(
        List.of(
            normalizedBookFile,
            normalizedBookFile.resolveSibling(fileName + "-journal"),
            normalizedBookFile.resolveSibling(fileName + "-wal"),
            normalizedBookFile.resolveSibling(fileName + "-shm")),
        artifacts.stream()
            .map(OpenBookFailureDetails.RetainedOpenBookPreparationArtifact::path)
            .toList());
  }
}
