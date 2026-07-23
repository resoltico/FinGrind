package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.nio.file.Path;
import java.util.Objects;

/** Local result family for rekeying one protected book under one generated key artifact. */
public sealed interface ProtectedBookRekeyOutcome
    permits ProtectedBookRekeyOutcome.Rekeyed, ProtectedBookRekeyOutcome.Rejected {
  /** Successful staged replacement of one selected live book and publication of its new key. */
  record Rekeyed(Path bookFilePath, Path newBookKeyFilePath, AttestationCommit attestationCommit)
      implements ProtectedBookRekeyOutcome {
    public Rekeyed {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(newBookKeyFilePath, "newBookKeyFilePath");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
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
