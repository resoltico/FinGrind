package dev.erst.fingrind.sqlite;

import java.nio.file.Path;

/** Generates a staged maintenance secret that is provably distinct from the source secret. */
final class SqliteDistinctStagedSecret {
  private static final int MAXIMUM_GENERATION_ATTEMPTS = 32;

  /** Generates one secret into an already-reserved staged key path. */
  @FunctionalInterface
  interface Generator {
    /** Generates one secure key file at the supplied staged path. */
    void generate(Path stagedSecretFilePath);
  }

  private SqliteDistinctStagedSecret() {}

  static SqliteBookPassphrase generate(
      Path stagedSecretFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookStagingSupport.StagingCheckpoint checkpoint,
      SqliteProtectedBookStagingSupport.StagingCheckpointListener checkpointListener,
      Generator generator) {
    for (int attempt = 0; attempt < MAXIMUM_GENERATION_ATTEMPTS; attempt++) {
      SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecretFilePath);
      checkpointListener.reached(checkpoint);
      generator.generate(stagedSecretFilePath);
      SqliteBookPassphrase generatedPassphrase = SqliteBookKeyFile.load(stagedSecretFilePath);
      if (!generatedPassphrase.hasSameSecretAs(sourcePassphrase)) {
        return generatedPassphrase;
      }
      generatedPassphrase.close();
    }
    SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecretFilePath);
    throw new IllegalStateException(
        "Unable to generate a distinct FinGrind maintenance key after "
            + MAXIMUM_GENERATION_ATTEMPTS
            + " attempts.");
  }
}
