package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationKeyFileDestinationOccupiedException;
import dev.erst.fingrind.core.attestation.AttestationKeyFilePublicationDurabilityException;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningCredentialOpening;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Translates encrypted founder-key custody failures into executor lifecycle failures. */
final class AttestationFounderCredentials {
  private AttestationFounderCredentials() {}

  /** Opens an already-existing founder credential without attempting replacement-key creation. */
  static AttestationSigningCredentialOpening openExisting(AttestationFounderInput founder) {
    try {
      return switch (founder.custodian()) {
        case FILE_PKCS8 ->
            new AttestationSigningCredentialOpening(
                AttestationKeyFiles.openExistingCredential(
                    founder.principalId(),
                    founder.encryptedKeyFilePath(),
                    founder.passphraseFilePath()),
                null);
      };
    } catch (java.io.IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialException(founder.encryptedKeyFilePath(), exception);
    }
  }

  /**
   * Validates one founder input without creating its optional first-use credential file.
   *
   * <p>An existing credential is opened and immediately closed so its principal, passphrase, and
   * encrypted material are checked before a new book session is admitted. A missing credential is
   * an intentional genesis-creation request, for which only the passphrase source can be checked
   * without publishing a key.
   */
  static void validateForOpening(AttestationFounderInput founder) {
    AttestationFounderInput checkedFounder = Objects.requireNonNull(founder, "founder");
    try {
      if (Files.exists(checkedFounder.encryptedKeyFilePath())) {
        AttestationKeyFiles.openExistingCredential(
                checkedFounder.principalId(),
                checkedFounder.encryptedKeyFilePath(),
                checkedFounder.passphraseFilePath())
            .close();
      } else {
        AttestationKeyFiles.validatePassphraseFile(checkedFounder.passphraseFilePath());
      }
    } catch (java.io.IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialException(checkedFounder.encryptedKeyFilePath(), exception);
    }
  }

  /** Opens a founder credential, creating its declared encrypted key only when it is missing. */
  static AttestationSigningCredentialOpening openOrCreate(AttestationFounderInput founder) {
    return openOrCreate(founder, AttestationFounderCredentials::openOrCreateFilePkcs8);
  }

  /** Opens or creates one founder credential through the supplied custody implementation. */
  static AttestationSigningCredentialOpening openOrCreate(
      AttestationFounderInput founder, FounderCredentialOpening credentialOpening) {
    FounderCredentialOpening checkedCredentialOpening =
        Objects.requireNonNull(credentialOpening, "credentialOpening");
    try {
      return switch (founder.custodian()) {
        case FILE_PKCS8 -> checkedCredentialOpening.open(founder);
      };
    } catch (AttestationKeyFileDestinationOccupiedException exception) {
      throw retainedFounderKeyFailure(
          exception.keyFilePath(), exception.retainedStage(), exception);
    } catch (AttestationKeyFilePublicationDurabilityException exception) {
      throw retainedFounderKeyFailure(
          exception.publication().publishedArtifactPath(),
          exception.publication().retention(),
          exception);
    } catch (ArtifactPublicationRetainedStageException exception) {
      throw retainedFounderKeyFailure(
          exception.retainedStage().retainedStagePath(), exception.retainedStage(), exception);
    } catch (ArtifactPublicationOutcomeUncertainException exception) {
      throw retainedFounderKeyFailure(
          exception.candidateArtifactPath(), exception.retainedStage(), exception);
    } catch (java.io.IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialException(founder.encryptedKeyFilePath(), exception);
    }
  }

  private static AttestationSigningCredentialOpening openOrCreateFilePkcs8(
      AttestationFounderInput founder) throws java.io.IOException {
    return AttestationKeyFiles.openOrCreateCredential(
        founder.principalId(), founder.encryptedKeyFilePath(), founder.passphraseFilePath());
  }

  private static AttestationFounderKeyRetentionException retainedFounderKeyFailure(
      java.nio.file.Path artifactPath,
      @Nullable ArtifactPublicationRetention retainedStage,
      Throwable failure) {
    return new AttestationFounderKeyRetentionException(
        List.of(
            OpenBookFailureDetails.retainedArtifact(
                OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
                artifactPath,
                retainedStage)),
        failure);
  }

  /** Opens or creates one founder credential while preserving checked filesystem failures. */
  @FunctionalInterface
  interface FounderCredentialOpening {
    /** Resolves one declared founder credential. */
    AttestationSigningCredentialOpening open(AttestationFounderInput founder)
        throws java.io.IOException;
  }
}
