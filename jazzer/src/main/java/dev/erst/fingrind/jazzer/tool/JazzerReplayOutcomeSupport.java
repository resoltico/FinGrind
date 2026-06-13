package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Shared failure normalization and rejection-shape helpers for Jazzer replay. */
final class JazzerReplayOutcomeSupport {
  private JazzerReplayOutcomeSupport() {}

  static ReplayOutcome unexpectedFailure(
      JazzerHarness harness, Throwable error, ReplayDetails details) {
    return new ReplayOutcome.UnexpectedFailure(
        harness.key(),
        error.getClass().getSimpleName(),
        normalizedMessage(error),
        stackTrace(error),
        details);
  }

  static PostingLifecycleStatus rejectionStatus(PostingRejection rejection) {
    return PostingLifecycleStatusMapper.forRejection(rejection);
  }

  static PreflightRejected requiredPreflightRejected(PreflightEntryResult result) {
    if (!(result instanceof PreflightRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic preflight rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static CommitRejected requiredCommitRejected(CommitEntryResult result) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic commit rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static String normalizedMessage(Throwable error) {
    Objects.requireNonNull(error, "error must not be null");
    return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
  }

  private static String stackTrace(Throwable error) {
    StringWriter output = new StringWriter();
    error.printStackTrace(new PrintWriter(output, true));
    return output.toString();
  }
}
