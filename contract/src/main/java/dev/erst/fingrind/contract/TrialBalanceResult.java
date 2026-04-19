package dev.erst.fingrind.contract;

import java.util.Objects;
import java.util.function.Function;

/** Closed result family for trial-balance reports. */
public sealed interface TrialBalanceResult
    permits TrialBalanceResult.Reported, TrialBalanceResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying one canonical trial-balance report. */
  record Reported(TrialBalanceReport report) implements TrialBalanceResult {
    /** Validates the trial-balance payload. */
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T> T fold(Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Deterministic refusal for trial-balance reporting. */
  record Rejected(BookQueryRejection rejection) implements TrialBalanceResult {
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
