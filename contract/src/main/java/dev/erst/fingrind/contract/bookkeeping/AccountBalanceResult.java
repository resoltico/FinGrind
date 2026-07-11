package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed result family for account-balance queries. */
public sealed interface AccountBalanceResult extends BookQueryReportResult<AccountBalanceSnapshot>
    permits AccountBalanceResult.Reported, AccountBalanceResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable AccountBalanceSnapshot reported() {
    return fold(Reported::snapshot, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Success result carrying one computed account-balance snapshot. */
  record Reported(AccountBalanceSnapshot snapshot) implements AccountBalanceResult {
    /** Validates the account-balance payload. */
    public Reported {
      Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
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
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
