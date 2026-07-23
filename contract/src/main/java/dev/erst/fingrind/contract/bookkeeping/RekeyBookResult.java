package dev.erst.fingrind.contract.bookkeeping;

import java.nio.file.Path;
import java.util.Objects;

/** Result family for rekeying one FinGrind book. */
public sealed interface RekeyBookResult permits RekeyBookResult.Rekeyed, RekeyBookResult.Rejected {

  /** Successful rekey outcome for one selected book file. */
  record Rekeyed(Path bookFilePath, AttestationCommit attestationCommit)
      implements RekeyBookResult {
    /** Validates the selected book path. */
    public Rekeyed {
      bookFilePath =
          Objects.requireNonNull(bookFilePath, "bookFilePath").toAbsolutePath().normalize();
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Deterministic refusal for rekey-book. */
  record Rejected(BookMaintenanceRejection rejection) implements RekeyBookResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
