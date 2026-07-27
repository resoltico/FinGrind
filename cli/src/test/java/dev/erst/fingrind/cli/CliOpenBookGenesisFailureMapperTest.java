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
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.executor.AttestationCredentialException;
import dev.erst.fingrind.executor.AttestationFounderKeyRetentionException;
import dev.erst.fingrind.executor.AttestationFounderKeyTargetOccupiedException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Verifies founder-key preparation failures retain their exact public open-book semantics. */
class CliOpenBookGenesisFailureMapperTest {
  @Test
  void failureFor_mapsRetentionAndArtifactPublicationUncertaintyFacts() {
    Path founderKey = Path.of("founders/operator.fgatk").toAbsolutePath().normalize();
    Path residualStage = Path.of("founders/.operator.fgatk-stage").toAbsolutePath().normalize();
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(residualStage);
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact retainedFounderKey =
        new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
            OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
            founderKey,
            retention);

    ContractFailure retained =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationFounderKeyRetentionException(
                List.of(retainedFounderKey), new IOException("genesis preparation failed")));
    assertFailure(
        retained, ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED, founderKey);
    assertNull(retained.argument());
    assertEquals(
        List.of(retainedFounderKey),
        assertInstanceOf(
                OpenBookFailureDetails.OpenBookPreparationArtifactsRetained.class,
                retained.details())
            .retainedArtifacts());

    ContractFailure outcome =
        CliOpenBookGenesisFailureMapper.failureFor(
            new ArtifactPublicationOutcomeUncertainException(
                founderKey, retention, new IOException("link outcome unknown")));
    assertFailure(
        outcome, ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN, founderKey);
    assertEquals(ProtocolOptions.Attestation.FOUNDER_KEY_FILE, outcome.argument());
    assertEquals(
        retention,
        assertInstanceOf(
                ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class, outcome.details())
            .retainedStage());
  }

  @Test
  void failureFor_mapsFounderKeyCollisionAndCredentialFailures() {
    Path founderKey = Path.of("founders/operator.fgatk").toAbsolutePath().normalize();

    ContractFailure occupied =
        CliOpenBookGenesisFailureMapper.failureFor(
            new AttestationFounderKeyTargetOccupiedException(
                founderKey, new FileAlreadyExistsException(founderKey.toString())));
    assertFailure(occupied, ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, founderKey);

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
