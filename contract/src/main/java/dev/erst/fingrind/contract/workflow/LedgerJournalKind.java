package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Stable journal-visible kind for one ledger-plan journal entry.
 *
 * <p>Every standard journal kind is the corresponding canonical ledger-step kind. Plan-boundary is
 * the only journal-only kind.
 */
public sealed interface LedgerJournalKind extends WireValue
    permits LedgerStepKind, LedgerJournalKind.BoundaryKind {
  /** Returns every stable public journal-kind wire value in declaration order. */
  static List<String> wireValues() {
    return Stream.concat(
            LedgerStepKind.wireValues().stream(), Stream.of(BoundaryKind.PLAN_BOUNDARY.wireValue()))
        .toList();
  }

  /** Parses one stable public journal-kind wire value. */
  static LedgerJournalKind fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    for (LedgerStepKind kind : LedgerStepKind.supportedPlanStepKinds()) {
      if (kind.wireValue().equals(wireValue)) {
        return kind;
      }
    }
    if (BoundaryKind.PLAN_BOUNDARY.wireValue().equals(wireValue)) {
      return BoundaryKind.PLAN_BOUNDARY;
    }
    throw new IllegalArgumentException("Unsupported ledger journal kind: " + wireValue);
  }

  /** Journal-only plan execution boundary marker. */
  enum BoundaryKind implements LedgerJournalKind {
    PLAN_BOUNDARY("plan-boundary");

    private final String wireValue;

    BoundaryKind(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }
}
