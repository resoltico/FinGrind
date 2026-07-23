package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed result family for account declaration or reactivation. */
public sealed interface DeclareAccountResult
    permits DeclareAccountResult.Declared,
        DeclareAccountResult.Reactivated,
        DeclareAccountResult.Renamed,
        DeclareAccountResult.Unchanged,
        DeclareAccountResult.Rejected {

  /** Success result carrying one first-declared durable account snapshot. */
  record Declared(DeclaredAccount account, AttestationCommit attestationCommit)
      implements DeclareAccountResult {
    /** Validates the declared account. */
    public Declared {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Success result carrying one reactivated durable account snapshot. */
  record Reactivated(DeclaredAccount account, AttestationCommit attestationCommit)
      implements DeclareAccountResult {
    /** Validates the reactivated account. */
    public Reactivated {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Success result carrying one renamed durable account snapshot. */
  record Renamed(DeclaredAccount account, AttestationCommit attestationCommit)
      implements DeclareAccountResult {
    /** Validates the renamed account. */
    public Renamed {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Success result carrying one unchanged durable account snapshot. */
  record Unchanged(DeclaredAccount account, @Nullable AttestationCommit attestationCommit)
      implements DeclareAccountResult {
    /** Validates the unchanged account. */
    public Unchanged {
      Objects.requireNonNull(account, "account");
      if (attestationCommit != null) {
        throw new IllegalArgumentException(
            "An unchanged account declaration must not report a newly appended attestation operation.");
      }
    }
  }

  /** Deterministic refusal for declare-account. */
  record Rejected(BookAdministrationRejection rejection) implements DeclareAccountResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
