package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Verifies that the visible or staged pair still proves one record-bound operation. */
final class SqlitePairPublicationRecoveryProof {
  private final SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier verifier;
  private final SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer;

  SqlitePairPublicationRecoveryProof(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier verifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
  }

  boolean verifiesRecordBoundPair(SqliteProtectedBookPairPublicationRecord record) {
    @org.jspecify.annotations.Nullable Path bookProofPath =
        record.finalBookMatches()
            ? record.bookTargetPath
            : record.stagedBookMatches() ? record.bookStagePath : null;
    @org.jspecify.annotations.Nullable Path secretProofPath =
        record.finalSecretMatches()
            ? record.secretTargetPath
            : record.stagedSecretMatches() ? record.secretStagePath : null;
    return bookProofPath != null
        && secretProofPath != null
        && verifies(bookProofPath, secretProofPath, record.binding);
  }

  boolean repairsIncompleteEvidence(SqliteProtectedBookPairPublicationRecord record) {
    try {
      SqliteProtectedBookPairPublicationEvidenceLifecycle.repairIncompleteEvidence(
          record, directoryForcer);
      return true;
    } catch (IOException | RuntimeException repairFailure) {
      return false;
    }
  }

  private boolean verifies(
      Path bookProofPath,
      Path secretProofPath,
      dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding binding) {
    try {
      return verifier.verifies(bookProofPath, secretProofPath, binding);
    } catch (RuntimeException verificationFailure) {
      return false;
    }
  }
}
