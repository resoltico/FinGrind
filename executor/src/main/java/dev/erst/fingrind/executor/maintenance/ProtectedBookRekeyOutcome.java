package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import java.nio.file.Path;
import java.util.Objects;

/** Local result family for rekeying one protected book under one generated key artifact. */
public sealed interface ProtectedBookRekeyOutcome
    permits ProtectedBookRekeyOutcome.Rekeyed, ProtectedBookRekeyOutcome.Rejected {
  /** Successful staged replacement of one selected live book and publication of its new key. */
  record Rekeyed(
      Path bookFilePath,
      Path newBookKeyFilePath,
      AttestationCommit attestationCommit,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      ProtectedBookPairPublication pairPublication)
      implements ProtectedBookRekeyOutcome {
    public Rekeyed {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(newBookKeyFilePath, "newBookKeyFilePath");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
      pairPublicationCompletion =
          ProtectedBookPairPublicationCompletion.requireRestoreOrRekeyCompletion(
              pairPublicationCompletion);
      pairPublication =
          Objects.requireNonNull(
              ProtectedBookPairPublicationCompletion.requirePublication(
                  pairPublicationCompletion, pairPublication),
              "pairPublication");
      bookFilePath = pairPublication.requireBookPublication(bookFilePath).publishedArtifactPath();
      newBookKeyFilePath =
          pairPublication
              .requireGeneratedSecretPublication(newBookKeyFilePath)
              .publishedArtifactPath();
    }
  }

  /** Deterministic refusal for rekey-book. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookRekeyOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
