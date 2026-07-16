package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Names the staged book and generated-secret artifacts that publish as one protected pair. */
record SqliteStagedProtectedBookPairArtifacts(
    SqliteOwnedStagedArtifact stagedBookFile,
    Path bookTargetPath,
    SqliteOwnedStagedArtifact stagedSecretFile,
    Path secretTargetPath) {
  SqliteStagedProtectedBookPairArtifacts {
    Objects.requireNonNull(stagedBookFile, "stagedBookFile");
    Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Objects.requireNonNull(stagedSecretFile, "stagedSecretFile");
    Objects.requireNonNull(secretTargetPath, "secretTargetPath");
  }
}
