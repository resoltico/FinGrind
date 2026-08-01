package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Pre-persistence Account Registry decision for one declaration request. */
public sealed interface AccountDeclarationDecision
    permits AccountDeclarationDecision.Declared,
        AccountDeclarationDecision.Reactivated,
        AccountDeclarationDecision.Renamed,
        AccountDeclarationDecision.Unchanged,
        AccountDeclarationDecision.Rejected {
  /** A previously unknown account should be persisted as active. */
  record Declared(RegisteredAccount account) implements AccountDeclarationDecision {
    public Declared {
      Objects.requireNonNull(account, "account");
    }
  }

  /** A previously inactive account should be persisted as active. */
  record Reactivated(RegisteredAccount account) implements AccountDeclarationDecision {
    public Reactivated {
      Objects.requireNonNull(account, "account");
    }
  }

  /** An existing active account should be persisted with its requested name. */
  record Renamed(RegisteredAccount account) implements AccountDeclarationDecision {
    public Renamed {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The requested account state already matches durable state. */
  record Unchanged(RegisteredAccount account) implements AccountDeclarationDecision {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The Account Registry rejected the request before persistence. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountDeclarationDecision {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
