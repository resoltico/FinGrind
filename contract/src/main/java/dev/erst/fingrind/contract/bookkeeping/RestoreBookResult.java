package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.Objects;

/** Result family for restoring one encrypted-book backup pair onto one live book path. */
public sealed interface RestoreBookResult
    permits RestoreBookResult.Restored, RestoreBookResult.Rejected {

  /** Successful restore outcome. */
  record Restored(
      Path bookFilePath,
      Path bookKeyFilePath,
      AttestationCommit attestationCommit,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      ProtectedBookPairPublication pairPublication)
      implements RestoreBookResult {
    public Restored {
      bookFilePath = normalizedPath(bookFilePath, "bookFilePath");
      bookKeyFilePath = normalizedPath(bookKeyFilePath, "bookKeyFilePath");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
      pairPublicationCompletion =
          ProtectedBookPairPublicationCompletion.requireRestoreOrRekeyCompletion(
              pairPublicationCompletion);
      pairPublication =
          java.util.Objects.requireNonNull(
              ProtectedBookPairPublicationCompletion.requirePublication(
                  pairPublicationCompletion, pairPublication),
              "pairPublication");
      bookFilePath = pairPublication.requireBookPublication(bookFilePath).publishedArtifactPath();
      bookKeyFilePath =
          pairPublication
              .requireGeneratedSecretPublication(bookKeyFilePath)
              .publishedArtifactPath();
    }
  }

  /** Deterministic refusal for restore-book. */
  record Rejected(BookMaintenanceRejection rejection) implements RestoreBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  private static Path normalizedPath(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
