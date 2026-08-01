package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal recovery facts translated by pair-publication admission into one public result. */
sealed interface SqlitePairPublicationReconciliation
    permits SqlitePairPublicationReconciliationAbsent,
        SqlitePairPublicationReconciliationRecovered,
        SqlitePairPublicationReconciliationExistingCompleteBackup,
        SqlitePairPublicationReconciliationPrepublicationRecoveryRequired,
        SqlitePairPublicationReconciliationEvidenceBlocked,
        SqlitePairPublicationReconciliationCompletionUncertain {}

/** No retained pair-publication evidence constrains the requested targets. */
enum SqlitePairPublicationReconciliationAbsent implements SqlitePairPublicationReconciliation {
  INSTANCE
}

/**
 * A complete retained operation was reconciled and exposes its authoritative binding and retention.
 */
record SqlitePairPublicationReconciliationRecovered(
    ProtectedBookPairPublicationBinding binding, ProtectedBookPairPublicationRetention retention)
    implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationRecovered {
    Objects.requireNonNull(binding, "binding");
    Objects.requireNonNull(retention, "retention");
  }
}

/** A complete backup already exists at its exact selected artifact pair. */
record SqlitePairPublicationReconciliationExistingCompleteBackup(
    Path backupArtifactPath, Path backupKeyPath) implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationExistingCompleteBackup {
    Objects.requireNonNull(backupArtifactPath, "backupArtifactPath");
    Objects.requireNonNull(backupKeyPath, "backupKeyPath");
  }
}

/** A complete retained record proves neither final-member primitive was reached. */
record SqlitePairPublicationReconciliationPrepublicationRecoveryRequired(
    Path bookArtifactPath,
    Path secretArtifactPath,
    ProtectedBookPairPublicationRecoveryRecordState recoveryRecordState,
    ProtectedBookPairPublicationRetention pairPublicationRetention)
    implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationPrepublicationRecoveryRequired {
    Objects.requireNonNull(bookArtifactPath, "bookArtifactPath");
    Objects.requireNonNull(secretArtifactPath, "secretArtifactPath");
    Objects.requireNonNull(recoveryRecordState, "recoveryRecordState");
    Objects.requireNonNull(pairPublicationRetention, "pairPublicationRetention");
    pairPublicationRetention.requireBookPublication(bookArtifactPath);
    pairPublicationRetention.requireGeneratedSecretPublication(secretArtifactPath);
  }
}

/** Retained evidence blocks publication because a final-member fact is unestablished. */
record SqlitePairPublicationReconciliationEvidenceBlocked(
    Path bookArtifactPath,
    ProtectedBookPairPublicationMemberState bookArtifactState,
    Path secretArtifactPath,
    ProtectedBookPairPublicationMemberState secretArtifactState,
    @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
    implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationEvidenceBlocked {
    Objects.requireNonNull(bookArtifactPath, "bookArtifactPath");
    Objects.requireNonNull(bookArtifactState, "bookArtifactState");
    Objects.requireNonNull(secretArtifactPath, "secretArtifactPath");
    Objects.requireNonNull(secretArtifactState, "secretArtifactState");
    if (bookArtifactState != ProtectedBookPairPublicationMemberState.UNESTABLISHED
        || secretArtifactState != ProtectedBookPairPublicationMemberState.UNESTABLISHED) {
      throw new IllegalArgumentException(
          "Evidence-blocked pair publication requires unestablished facts for both members.");
    }
    if (pairPublicationRetention != null) {
      throw new IllegalArgumentException(
          "Evidence-blocked pair publication cannot claim authoritative retained-stage evidence.");
    }
  }
}

/** Final-member state is known but neither a complete recovery nor safe rejection can be proven. */
record SqlitePairPublicationReconciliationCompletionUncertain(
    Path bookArtifactPath,
    ProtectedBookPairPublicationMemberState bookArtifactState,
    Path secretArtifactPath,
    ProtectedBookPairPublicationMemberState secretArtifactState,
    @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
    implements SqlitePairPublicationReconciliation {
  SqlitePairPublicationReconciliationCompletionUncertain {
    Objects.requireNonNull(bookArtifactPath, "bookArtifactPath");
    Objects.requireNonNull(bookArtifactState, "bookArtifactState");
    Objects.requireNonNull(secretArtifactPath, "secretArtifactPath");
    Objects.requireNonNull(secretArtifactState, "secretArtifactState");
    if (bookArtifactState == ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED
        && secretArtifactState == ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED) {
      throw new IllegalArgumentException(
          "Pair-publication uncertainty requires a final-member publication fact.");
    }
    if (bookArtifactState == ProtectedBookPairPublicationMemberState.UNESTABLISHED
        || secretArtifactState == ProtectedBookPairPublicationMemberState.UNESTABLISHED) {
      throw new IllegalArgumentException(
          "Completion uncertainty cannot claim an unestablished member fact.");
    }
    requireRetention(pairPublicationRetention, bookArtifactPath, secretArtifactPath);
  }

  private static void requireRetention(
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path bookArtifactPath,
      Path secretArtifactPath) {
    if (pairPublicationRetention == null) {
      return;
    }
    pairPublicationRetention.requireBookPublication(bookArtifactPath);
    pairPublicationRetention.requireGeneratedSecretPublication(secretArtifactPath);
  }
}
