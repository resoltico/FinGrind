package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable replay-backed classification for raw local libFuzzer finding artifacts. */
public enum ReplayFindingClassification implements WireValue {
  REPLAY_CLEAN("replay-clean"),
  EXPECTED_INVALID("expected-invalid"),
  UNEXPECTED_FAILURE("unexpected-failure");

  private final String wireValue;

  ReplayFindingClassification(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable finding-classification wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ReplayFindingClassification.class);
  }

  /** Parses one stable finding-classification wire value. */
  public static ReplayFindingClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ReplayFindingClassification.class, wireValue, "Unsupported replay finding classification");
  }

  /** Classifies one replay outcome for raw finding listings. */
  public static ReplayFindingClassification fromOutcome(ReplayOutcome outcome) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case ReplayOutcome.Success _ -> REPLAY_CLEAN;
      case ReplayOutcome.ExpectedInvalid _ -> EXPECTED_INVALID;
      case ReplayOutcome.UnexpectedFailure _ -> UNEXPECTED_FAILURE;
    };
  }
}
