package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationKeyFileDestinationOccupiedException;
import dev.erst.fingrind.core.attestation.AttestationKeyFilePublicationDurabilityException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies founder-key custody failures retain their immutable lifecycle evidence. */
class AttestationFounderCredentialsTest {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void translatesDurabilityFailureToRetainedFounderKeyArtifacts() {
    Path founderKeyPath = temporaryDirectory.resolve("founder.fgatk");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-stage.tmp"));
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(founderKeyPath, retention);
    AttestationKeyFilePublicationDurabilityException durabilityFailure =
        new AttestationKeyFilePublicationDurabilityException(
            publication, new IOException("directory force failed"));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(founderKeyPath),
                    ignored -> {
                      throw durabilityFailure;
                    }));

    assertSame(durabilityFailure, observed.getCause());
    assertRetainedFounderKey(observed, founderKeyPath, retention);
  }

  @Test
  void translatesRetainedStageFailureToRetainedFounderKeyArtifacts() {
    Path founderKeyPath = temporaryDirectory.resolve("founder.fgatk");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-stage.tmp"));
    ArtifactPublicationRetainedStageException stageFailure =
        new ArtifactPublicationRetainedStageException(
            retention, new IOException("stage write failed"));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(founderKeyPath),
                    ignored -> {
                      throw stageFailure;
                    }));

    assertSame(stageFailure, observed.getCause());
    assertRetainedFounderKey(observed, retention.retainedStagePath(), retention);
  }

  @Test
  void translatesOrdinaryCustodyFailureToTheCredentialContract() {
    Path founderKeyPath = temporaryDirectory.resolve("founder.fgatk");

    AttestationCredentialException observed =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(founderKeyPath),
                    ignored -> {
                      throw new IOException("passphrase file cannot be read");
                    }));

    assertEquals(founderKeyPath.toAbsolutePath().normalize(), observed.credentialPath());
    assertInstanceOf(IOException.class, observed.getCause());
  }

  @Test
  void preservesAnAdmittedFounderKeyTargetCollisionAsRetainedEvidence() {
    Path founderKeyPath = temporaryDirectory.resolve("founder.fgatk");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-stage.tmp"));
    AttestationKeyFileDestinationOccupiedException collision =
        new AttestationKeyFileDestinationOccupiedException(
            founderKeyPath,
            retention,
            new java.nio.file.FileAlreadyExistsException(founderKeyPath.toString()));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(founderKeyPath),
                    ignored -> {
                      throw collision;
                    }));

    assertSame(collision, observed.getCause());
    assertRetainedFounderKey(observed, founderKeyPath, retention);
  }

  @Test
  void preservesAnIndeterminateFounderKeyCandidateWithoutInventingStageEvidence() {
    Path founderKeyPath = temporaryDirectory.resolve("founder.fgatk");
    ArtifactPublicationOutcomeUncertainException uncertainty =
        new ArtifactPublicationOutcomeUncertainException(
            founderKeyPath, null, new IOException("no-replace link outcome unknown"));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(founderKeyPath),
                    ignored -> {
                      throw uncertainty;
                    }));

    assertSame(uncertainty, observed.getCause());
    assertEquals(1, observed.retainedFounderKeyArtifacts().size());
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact retainedArtifact =
        observed.retainedFounderKeyArtifacts().getFirst();
    assertEquals(founderKeyPath.toAbsolutePath().normalize(), retainedArtifact.path());
    assertNull(retainedArtifact.retainedStage());
  }

  private static void assertRetainedFounderKey(
      AttestationFounderKeyRetentionException exception,
      Path expectedPath,
      ArtifactPublicationRetention expectedRetention) {
    assertEquals(1, exception.retainedFounderKeyArtifacts().size());
    OpenBookFailureDetails.RetainedOpenBookPreparationArtifact artifact =
        exception.retainedFounderKeyArtifacts().getFirst();
    assertEquals(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
        artifact.role());
    assertEquals(expectedPath.toAbsolutePath().normalize(), artifact.path());
    assertEquals(expectedRetention, artifact.retainedStage());
  }

  private static AttestationFounderInput founderInput(Path founderKeyPath) {
    return new AttestationFounderInput(
        AttestationCustodian.FILE_PKCS8,
        PRINCIPAL_ID,
        founderKeyPath,
        founderKeyPath.resolveSibling(founderKeyPath.getFileName() + ".passphrase"));
  }
}
