package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import java.util.Objects;

/** Closed family of bookkeeping outcomes for one account declaration attempt. */
public sealed interface AccountDeclarationOutcome
    permits AccountDeclarationOutcome.Declared,
        AccountDeclarationOutcome.Reactivated,
        AccountDeclarationOutcome.Renamed,
        AccountDeclarationOutcome.Unchanged,
        AccountDeclarationOutcome.Rejected {
  /** Successful first declaration outcome. */
  record Declared(RegisteredAccount account, AttestationAppendOutcome.Appended attestationAppend)
      implements AccountDeclarationOutcome {
    /** Validates one declared-account outcome. */
    public Declared {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** Successful reactivation outcome for one previously inactive account. */
  record Reactivated(RegisteredAccount account, AttestationAppendOutcome.Appended attestationAppend)
      implements AccountDeclarationOutcome {
    /** Validates one reactivated-account outcome. */
    public Reactivated {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** Successful rename outcome for one already active account. */
  record Renamed(RegisteredAccount account, AttestationAppendOutcome.Appended attestationAppend)
      implements AccountDeclarationOutcome {
    /** Validates one renamed-account outcome. */
    public Renamed {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** Successful no-op outcome when the requested account snapshot already matches durable state. */
  record Unchanged(RegisteredAccount account) implements AccountDeclarationOutcome {
    /** Validates one unchanged-account outcome. */
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Deterministic bookkeeping rejection for account declaration. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountDeclarationOutcome {
    /** Validates one account-declaration rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
