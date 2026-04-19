package dev.erst.fingrind.contract;

import java.util.Objects;
import java.util.function.Function;

/** Closed result family for account-balance queries. */
public sealed interface AccountBalanceResult
    permits AccountBalanceResult.Reported, AccountBalanceResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying one computed account-balance snapshot. */
  record Reported(AccountBalanceSnapshot snapshot) implements AccountBalanceResult {
    /** Validates the account-balance payload. */
    public Reported {
      Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public <T> T fold(Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Deterministic refusal for account-balance queries. */
  record Rejected(BookQueryRejection rejection) implements AccountBalanceResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T> T fold(Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
