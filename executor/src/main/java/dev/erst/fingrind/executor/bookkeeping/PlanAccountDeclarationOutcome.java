package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Account-declaration outcome available to one aggregate ledger-plan child step. */
public sealed interface PlanAccountDeclarationOutcome
    permits PlanAccountDeclarationOutcome.Declared,
        PlanAccountDeclarationOutcome.Reactivated,
        PlanAccountDeclarationOutcome.Renamed,
        PlanAccountDeclarationOutcome.Unchanged,
        PlanAccountDeclarationOutcome.Rejected {
  /** The plan declared a new account and deferred attestation to its aggregate operation. */
  record Declared(RegisteredAccount account) implements PlanAccountDeclarationOutcome {
    public Declared {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The plan reactivated an account and deferred attestation to its aggregate operation. */
  record Reactivated(RegisteredAccount account) implements PlanAccountDeclarationOutcome {
    public Reactivated {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The plan renamed an account and deferred attestation to its aggregate operation. */
  record Renamed(RegisteredAccount account) implements PlanAccountDeclarationOutcome {
    public Renamed {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The requested account already matched durable state, so the plan did not mutate it. */
  record Unchanged(RegisteredAccount account) implements PlanAccountDeclarationOutcome {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The account declaration was rejected before mutation. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements PlanAccountDeclarationOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
