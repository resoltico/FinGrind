package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic refusals owned by the Account Registry lifecycle. */
public sealed interface AccountRegistryLifecycleRejection extends BookAdministrationRejection
    permits AccountRegistryLifecycleRejection.AccountNotFound,
        AccountRegistryLifecycleRejection.AccountHasDependents,
        AccountRegistryLifecycleRejection.AccountBalanceNotZero {

  /** Refusal for an account lifecycle command that names no declared account. */
  record AccountNotFound(AccountCode accountCode) implements AccountRegistryLifecycleRejection {
    public AccountNotFound {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }

  /** Refusal when durable account relationships prohibit the requested lifecycle change. */
  record AccountHasDependents(AccountCode accountCode, List<AccountRegistryDependency> dependencies)
      implements AccountRegistryLifecycleRejection {
    public AccountHasDependents {
      Objects.requireNonNull(accountCode, "accountCode");
      dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
      if (dependencies.isEmpty()) {
        throw new IllegalArgumentException(
            "Account dependents must contain at least one dependency.");
      }
    }
  }

  /** Refusal when account retirement would preserve a non-zero current balance. */
  record AccountBalanceNotZero(AccountCode accountCode)
      implements AccountRegistryLifecycleRejection {
    public AccountBalanceNotZero {
      Objects.requireNonNull(accountCode, "accountCode");
    }
  }
}
