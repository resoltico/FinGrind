package dev.erst.fingrind.contract;

import java.util.Objects;

/** Closed result family for account-registry listing. */
public sealed interface ListAccountsResult
    permits ListAccountsResult.Listed, ListAccountsResult.Rejected {

  /** Success result carrying the current registry snapshot. */
  record Listed(AccountPage page) implements ListAccountsResult {
    /** Validates the listed-account snapshot. */
    public Listed {
      Objects.requireNonNull(page, "page");
    }
  }

  /** Deterministic refusal for account-registry listing. */
  record Rejected(BookQueryRejection rejection) implements ListAccountsResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
