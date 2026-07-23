package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed result family for an account retirement. */
public sealed interface RetireAccountResult
    permits RetireAccountResult.Retired,
        RetireAccountResult.Unchanged,
        RetireAccountResult.Rejected {
  /** Success result carrying the retired account snapshot. */
  record Retired(DeclaredAccount account, AttestationCommit attestationCommit)
      implements RetireAccountResult {
    public Retired {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Successful no-op result when the account is already retired. */
  record Unchanged(DeclaredAccount account, @Nullable AttestationCommit attestationCommit)
      implements RetireAccountResult {
    public Unchanged {
      Objects.requireNonNull(account, "account");
      if (attestationCommit != null) {
        throw new IllegalArgumentException(
            "An unchanged account retirement must not report a newly appended attestation operation.");
      }
    }
  }

  /** Deterministic refusal for retire-account. */
  record Rejected(BookAdministrationRejection rejection) implements RetireAccountResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
