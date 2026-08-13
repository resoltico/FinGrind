package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.executor.AttestationCredentialException;
import dev.erst.fingrind.executor.AttestationFounderKeyPublicationProgressException;
import dev.erst.fingrind.executor.AttestationFounderKeyPublicationTransactionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Verifies founder-key preparation failures preserve journal-only open-book semantics. */
class CliOpenBookGenesisFailureMapperTest {
  @Test
  void failureFor_mapsRecordedAndIncompleteFounderKeyTransactions() {
    Path founderKey = Path.of("founders/operator.fgatk").toAbsolutePath().normalize();
    Path laterFounderKey = Path.of("founders/later-operator.fgatk").toAbsolutePath().normalize();
    AttestationFounderKeyPublicationTransactionException incomplete =
        new AttestationFounderKeyPublicationTransactionException(
            laterFounderKey,
            CliPublicationTransactionTestFixtures.incompleteResult(),
            new IOException("journal cleanup incomplete"));

    ContractFailure progress =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationFounderKeyPublicationProgressException(
                List.of(CliPublicationTransactionTestFixtures.completedArtifact(founderKey)),
                incomplete,
                incomplete));
    assertFailure(progress, ContractErrors.Descriptor.OPEN_BOOK_PUBLICATION_PROGRESS, founderKey);
    assertNull(progress.argument());
    OpenBookFailureDetails.OpenBookPublicationProgress details =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookPublicationProgress.class, progress.details());
    assertEquals(1, details.publishedFounderKeyArtifacts().size());
    assertEquals(
        incomplete.transactionResult(),
        Objects.requireNonNull(
                details.incompleteFounderKeyPublication(), "incomplete founder-key publication")
            .transactionResult());

    ContractFailure completedOnlyProgress =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationFounderKeyPublicationProgressException(
                List.of(CliPublicationTransactionTestFixtures.completedArtifact(founderKey)),
                null,
                new IllegalStateException("later preparation failed")));
    OpenBookFailureDetails.OpenBookPublicationProgress completedOnlyDetails =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookPublicationProgress.class,
            completedOnlyProgress.details());
    assertNull(completedOnlyDetails.incompleteFounderKeyPublication());

    ContractFailure standaloneIncomplete =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationFounderKeyPublicationTransactionException(
                founderKey,
                CliPublicationTransactionTestFixtures.incompleteResult(),
                new IOException("link outcome unknown")));
    assertFailure(
        standaloneIncomplete,
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
        founderKey);
    assertEquals(ProtocolOptions.Attestation.FOUNDER_KEY_FILE, standaloneIncomplete.argument());
    assertEquals(
        CliPublicationTransactionTestFixtures.incompleteResult(),
        assertInstanceOf(
                ContractFailureDetails.PublicationTransactionIncomplete.class,
                standaloneIncomplete.details())
            .transactionResult());
  }

  @Test
  void failureFor_mapsCredentialFailures() {
    Path unreadableCredential =
        Path.of("founders/unreadable-operator.fgatk").toAbsolutePath().normalize();
    ContractFailure credential =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationCredentialException(
                unreadableCredential, new IOException("credential read failed")));
    assertFailure(
        credential, ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL, unreadableCredential);
  }

  @Test
  void failureFor_rethrowsUnknownRuntimeFailuresWithoutRelabelingThem() {
    IllegalStateException unknown = new IllegalStateException("unexpected genesis fault");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> CliOpenBookGenesisFailureMapper.failureFor(unknown));

    assertSame(unknown, thrown);
  }

  private static void assertFailure(
      ContractFailure failure, ContractErrors.Descriptor descriptor, Path expectedPath) {
    assertEquals(descriptor.code(), failure.code());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        Objects.requireNonNull(failure.paths(), "failure paths").path());
  }
}
