package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Reports a no-clobber encrypted-key destination that was already occupied after admission. */
public final class AttestationKeyFileDestinationOccupiedException extends IOException {
  private static final long serialVersionUID = 1L;

  private final transient Path keyFilePath;
  private final String serializedKeyFilePath;
  private final transient ArtifactPublicationRetention retainedStage;
  private final String serializedRetainedStagePath;

  /** Retains the admitted canonical destination and the stage FinGrind will never mutate. */
  public AttestationKeyFileDestinationOccupiedException(
      Path keyFilePath, ArtifactPublicationRetention retainedStage, IOException cause) {
    super("The selected encrypted attestation key destination already exists.", cause);
    this.keyFilePath = canonicalPath(keyFilePath);
    this.serializedKeyFilePath = this.keyFilePath.toString();
    this.retainedStage = Objects.requireNonNull(retainedStage, "retainedStage");
    this.serializedRetainedStagePath = this.retainedStage.retainedStagePath().toString();
  }

  /** Returns the canonical occupied key destination. */
  public Path keyFilePath() {
    return keyFilePath == null ? Path.of(serializedKeyFilePath) : keyFilePath;
  }

  /** Returns the stage retained before the occupied final destination was observed. */
  public ArtifactPublicationRetention retainedStage() {
    return retainedStage == null
        ? new ArtifactPublicationRetention(Path.of(serializedRetainedStagePath))
        : retainedStage;
  }

  private static Path canonicalPath(Path keyFilePath) {
    return Objects.requireNonNull(keyFilePath, "keyFilePath").toAbsolutePath().normalize();
  }
}
