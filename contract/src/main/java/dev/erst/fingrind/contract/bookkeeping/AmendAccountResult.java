package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed result family for an account amendment. */
public sealed interface AmendAccountResult
    permits AmendAccountResult.Amended, AmendAccountResult.Unchanged, AmendAccountResult.Rejected {
  /** Success result carrying the amended account snapshot. */
  record Amended(DeclaredAccount account, AttestationCommit attestationCommit)
      implements AmendAccountResult {
    public Amended {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Successful no-op result when the requested definition already matches durable state. */
  record Unchanged(DeclaredAccount account, @Nullable AttestationCommit attestationCommit)
      implements AmendAccountResult {
    public Unchanged {
      Objects.requireNonNull(account, "account");
      if (attestationCommit != null) {
        throw new IllegalArgumentException(
            "An unchanged account amendment must not report a newly appended attestation operation.");
      }
    }
  }

  /** Deterministic refusal for amend-account. */
  record Rejected(BookAdministrationRejection rejection) implements AmendAccountResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
