package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Generates fresh owned maintenance-secret stages until one is provably distinct from the source.
 */
final class SqliteDistinctStagedSecret {
  private static final int MAXIMUM_GENERATION_ATTEMPTS = 32;

  /** Generates one secret into an already-reserved staged key path. */
  @FunctionalInterface
  interface Generator {
    /**
     * Writes one secure key file into the supplied fresh owned stage without replacing its path.
     */
    void generate(Path stagedSecretFilePath);
  }

  /** Creates one fresh, owned stage for a single secret-generation candidate. */
  @FunctionalInterface
  interface StageCreator {
    /** Creates an empty stage that remains owned by the caller on return. */
    SqliteOwnedStagedArtifact create();
  }

  /** Transfers the selected stage and its open secret to the caller. */
  record GeneratedSecret(
      SqliteOwnedStagedArtifact stagedSecretFile, SqliteBookPassphrase passphrase) {
    GeneratedSecret {
      Objects.requireNonNull(stagedSecretFile, "stagedSecretFile");
      Objects.requireNonNull(passphrase, "passphrase");
    }
  }

  private SqliteDistinctStagedSecret() {}

  static GeneratedSecret generate(
      StageCreator stageCreator,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      Generator generator) {
    Objects.requireNonNull(stageCreator, "stageCreator");
    Objects.requireNonNull(sourcePassphrase, "sourcePassphrase");
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Objects.requireNonNull(generator, "generator");
    for (int attempt = 0; attempt < MAXIMUM_GENERATION_ATTEMPTS; attempt++) {
      SqliteOwnedStagedArtifact candidate = stageCreator.create();
      boolean selected = false;
      try {
        checkpointListener.reached(checkpoint);
        generator.generate(candidate.stagedPath());
        SqliteBookPassphrase generatedPassphrase = SqliteBookKeyFile.load(candidate.stagedPath());
        try {
          if (!generatedPassphrase.hasSameSecretAs(sourcePassphrase)) {
            selected = true;
            // The selected secret has one independent caller-owned instance. The temporary
            // inspection instance is zeroized as this scope exits.
            return new GeneratedSecret(candidate, generatedPassphrase.copy());
          }
        } finally {
          generatedPassphrase.close();
        }
      } finally {
        if (!selected) {
          // Retain every rejected candidate. A later same-owner replacement must never turn
          // duplicate-secret cleanup into deletion of another actor's artifact.
          candidate.releaseRetained();
        }
      }
    }
    throw new IllegalStateException(
        "Unable to generate a distinct FinGrind maintenance key after "
            + MAXIMUM_GENERATION_ATTEMPTS
            + " attempts.");
  }
}
