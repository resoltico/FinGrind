package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PrivateOutputDirectoryDurability;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Executes retained-stage no-clobber publication for one encrypted attestation key. */
final class AttestationKeyFilePublisher {
  private AttestationKeyFilePublisher() {}

  static ArtifactPublicationResult publish(Path path, byte[] encryptedPrivateKey)
      throws IOException {
    Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    AttestationKeyFileDestination destination =
        AttestationKeyFileLocation.publicationDestination(normalizedPath);
    Path stagedPath =
        AttestationKeyFileStaging.createAndWriteOwnerOnlyStage(
            destination.parent(),
            Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey"));
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(stagedPath);
    linkFinalPath(destination, retention);
    forceFinalLink(destination, retention);
    return new ArtifactPublicationResult(destination.finalPath(), retention);
  }

  static void linkFinalPath(
      AttestationKeyFileDestination destination, ArtifactPublicationRetention retention)
      throws IOException {
    try {
      Files.createLink(destination.finalPath(), retention.retainedStagePath());
    } catch (FileAlreadyExistsException exception) {
      throw new AttestationKeyFileDestinationOccupiedException(
          destination.finalPath(), retention, exception);
    } catch (IOException | RuntimeException exception) {
      throw new ArtifactPublicationOutcomeUncertainException(
          destination.finalPath(), retention, exception);
    }
  }

  static void forceFinalLink(
      AttestationKeyFileDestination destination, ArtifactPublicationRetention retention)
      throws IOException {
    try {
      PrivateOutputDirectoryDurability.force(destination.parent());
    } catch (IOException | RuntimeException exception) {
      throw new AttestationKeyFilePublicationDurabilityException(
          new ArtifactPublicationResult(destination.finalPath(), retention), exception);
    }
  }
}
