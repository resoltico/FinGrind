package dev.erst.fingrind.application;

import java.util.List;
import java.util.Objects;

/** Closed result family for account-registry listing. */
public sealed interface ListAccountsResult
    permits ListAccountsResult.Listed, ListAccountsResult.Rejected {

  /** Success result carrying the current registry snapshot. */
  record Listed(List<DeclaredAccount> accounts) implements ListAccountsResult {
    /** Validates the listed-account snapshot. */
    public Listed {
      accounts = List.copyOf(Objects.requireNonNull(accounts, "accounts"));
    }
  }

  /** Deterministic refusal for account-registry listing. */
  record Rejected(BookAdministrationRejection rejection) implements ListAccountsResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
