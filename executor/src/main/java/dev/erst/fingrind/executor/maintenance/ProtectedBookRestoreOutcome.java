package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import java.nio.file.Path;
import java.util.Objects;

/** Local result family for restoring one encrypted-book backup pair. */
public sealed interface ProtectedBookRestoreOutcome
    permits ProtectedBookRestoreOutcome.Restored, ProtectedBookRestoreOutcome.Rejected {

  /** Successful restore outcome. */
  record Restored(
      Path bookFilePath,
      Path bookKeyFilePath,
      AttestationCommit attestationCommit,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      ProtectedBookPairPublicationRetention pairPublicationRetention)
      implements ProtectedBookRestoreOutcome {
    public Restored {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
      pairPublicationCompletion =
          ProtectedBookPairPublicationCompletion.requireRestoreOrRekeyCompletion(
              pairPublicationCompletion);
      pairPublicationRetention =
          Objects.requireNonNull(
              ProtectedBookPairPublicationCompletion.requireRetention(
                  pairPublicationCompletion, pairPublicationRetention),
              "pairPublicationRetention");
      bookFilePath =
          pairPublicationRetention.requireBookPublication(bookFilePath).publishedArtifactPath();
      bookKeyFilePath =
          pairPublicationRetention
              .requireGeneratedSecretPublication(bookKeyFilePath)
              .publishedArtifactPath();
    }
  }

  /** Deterministic refusal for restore-book. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookRestoreOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
