package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;

/** Closed result family for account-registry listing. */
public sealed interface ListAccountsResult
    permits ListAccountsResult.Listed, ListAccountsResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying the accepted query and current registry page. */
  record Listed(ListAccountsQuery query, AccountPage page) implements ListAccountsResult {
    /** Validates the accepted account query and listed-account snapshot. */
    public Listed {
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(page, "page");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(listedMapper, "listedMapper").apply(this);
    }
  }

  /** Deterministic refusal for account-registry listing. */
  record Rejected(BookQueryRejection rejection) implements ListAccountsResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
