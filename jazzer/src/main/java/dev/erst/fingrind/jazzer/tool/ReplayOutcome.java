package dev.erst.fingrind.jazzer.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Captures whether replaying a committed FinGrind Jazzer input succeeded or failed. */
public sealed interface ReplayOutcome
    permits ReplayOutcome.ExpectedInvalid, ReplayOutcome.Success, ReplayOutcome.UnexpectedFailure {
  /** Human-readable success message shared by replay-clean outcomes. */
  String SUCCESS_MESSAGE = "Replay completed without surfacing a bug.";

  /** Returns the harness key that produced this replay outcome. */
  String harnessKey();

  /** Returns the structured details decoded from the raw input bytes. */
  ReplayDetails details();

  /** Returns the stable replay outcome kind used in committed metadata and CLI output. */
  @JsonProperty("outcomeKind")
  default ReplayOutcomeKind kind() {
    return switch (this) {
      case ReplayOutcome.Success _ -> ReplayOutcomeKind.SUCCESS;
      case ReplayOutcome.ExpectedInvalid _ -> ReplayOutcomeKind.EXPECTED_INVALID;
      case ReplayOutcome.UnexpectedFailure _ -> ReplayOutcomeKind.UNEXPECTED_FAILURE;
    };
  }

  /** Returns the deterministic replay message shown to operators and recorded in metadata. */
  @JsonProperty("message")
  String message();

  /** Represents a replay that completed without surfacing a bug. */
  record Success(String harnessKey, ReplayDetails details) implements ReplayOutcome {
    public Success {
      harnessKey = ReplayModelValidation.requireText(harnessKey, "harnessKey");
      details = ReplayModelValidation.requireDetails(details, "details");
    }

    @Override
    public String message() {
      return SUCCESS_MESSAGE;
    }
  }

  /** Represents a replay that was invalid in a documented, expected way. */
  record ExpectedInvalid(
      String harnessKey, String invalidKind, String message, ReplayDetails details)
      implements ReplayOutcome {
    public ExpectedInvalid {
      harnessKey = ReplayModelValidation.requireText(harnessKey, "harnessKey");
      invalidKind = ReplayModelValidation.requireText(invalidKind, "invalidKind");
      message = ReplayModelValidation.requireText(message, "message");
      details = ReplayModelValidation.requireDetails(details, "details");
    }
  }

  /** Represents a replay that surfaced an unexpected exception or invariant failure. */
  record UnexpectedFailure(
      String harnessKey,
      String failureKind,
      String message,
      String stackTrace,
      ReplayDetails details)
      implements ReplayOutcome {
    public UnexpectedFailure {
      harnessKey = ReplayModelValidation.requireText(harnessKey, "harnessKey");
      failureKind = ReplayModelValidation.requireText(failureKind, "failureKind");
      message = ReplayModelValidation.requireText(message, "message");
      stackTrace = ReplayModelValidation.requireText(stackTrace, "stackTrace");
      details = ReplayModelValidation.requireDetails(details, "details");
    }
  }
}
