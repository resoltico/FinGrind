package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Result of a Latvian payroll-register query. */
public sealed interface LatvianPayrollRegisterResult
    extends BookQueryReportResult<LatvianPayrollRegisterReport>
    permits LatvianPayrollRegisterResult.Reported, LatvianPayrollRegisterResult.Rejected {
  /** Folds this closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Returns the report when admitted, otherwise null. */
  @Override
  default @Nullable LatvianPayrollRegisterReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  /** Returns the deterministic rejection when not admitted, otherwise null. */
  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful payroll-register projection. */
  record Reported(LatvianPayrollRegisterReport report) implements LatvianPayrollRegisterResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Deterministic query rejection. */
  record Rejected(BookQueryRejection rejection) implements LatvianPayrollRegisterResult {
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
