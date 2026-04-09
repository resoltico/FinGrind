package dev.erst.fingrind.jazzer.tool;

/** Captures whether replaying a committed FinGrind Jazzer input succeeded or failed. */
public sealed interface ReplayOutcome
    permits ReplayOutcome.ExpectedInvalid, ReplayOutcome.Success, ReplayOutcome.UnexpectedFailure {
  /** Returns the harness key that produced this replay outcome. */
  String harnessKey();

  /** Returns the structured details decoded from the raw input bytes. */
  ReplayDetails details();

  /** Represents a replay that completed without surfacing a bug. */
  record Success(String harnessKey, ReplayDetails details) implements ReplayOutcome {}

  /** Represents a replay that was invalid in a documented, expected way. */
  record ExpectedInvalid(String harnessKey, String invalidKind, String message, ReplayDetails details)
      implements ReplayOutcome {}

  /** Represents a replay that surfaced an unexpected exception or invariant failure. */
  record UnexpectedFailure(
      String harnessKey,
      String failureKind,
      String message,
      String stackTrace,
      ReplayDetails details)
      implements ReplayOutcome {}
}
