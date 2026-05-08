package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical journal-visible step identity for one ledger-plan entry. */
public sealed interface LedgerJournalStep
    permits LedgerJournalStep.Standard, LedgerJournalStep.Assertion, LedgerJournalStep.Boundary {
  /** Returns the top-level journal kind for this step identity. */
  LedgerJournalKind kind();

  /**
   * Returns the nested assertion kind when the journal step represents an assertion, or {@code
   * null} for standard and plan-boundary journal steps.
   */
  @Nullable LedgerAssertionKind detailKind();

  /**
   * Returns the nested boundary phase when the journal step represents one plan-boundary phase, or
   * {@code null} for standard and assertion journal steps.
   */
  default @Nullable LedgerBoundaryPhase boundaryPhase() {
    return null;
  }

  /** Creates one non-assert journal step identity. */
  static LedgerJournalStep standard(LedgerStepKind kind) {
    return new Standard(kind);
  }

  /** Creates one assert journal step identity. */
  static LedgerJournalStep assertion(LedgerAssertionKind detailKind) {
    return new Assertion(detailKind);
  }

  /** Creates one plan-boundary journal step identity. */
  static LedgerJournalStep boundary(LedgerBoundaryPhase phase) {
    return new Boundary(phase);
  }

  /** Journal step identity for one non-assert plan step. */
  record Standard(LedgerStepKind stepKind) implements LedgerJournalStep {
    /** Validates one standard journal step identity. */
    public Standard {
      Objects.requireNonNull(stepKind, "stepKind");
      if (stepKind == LedgerStepKind.ASSERT) {
        throw new IllegalArgumentException(
            "Standard journal step identities must not use the assert step kind.");
      }
    }

    @Override
    public LedgerJournalKind kind() {
      return LedgerJournalKind.fromWireValue(stepKind.wireValue());
    }

    @Override
    public @Nullable LedgerAssertionKind detailKind() {
      return null;
    }
  }

  /** Journal step identity for one assertion plan step. */
  record Assertion(LedgerAssertionKind detailKind) implements LedgerJournalStep {
    /** Validates one assertion journal step identity. */
    public Assertion {
      Objects.requireNonNull(detailKind, "detailKind");
    }

    @Override
    public LedgerJournalKind kind() {
      return LedgerJournalKind.ASSERT;
    }
  }

  /** Journal step identity for one begin/commit/rollback boundary phase. */
  record Boundary(LedgerBoundaryPhase phase) implements LedgerJournalStep {
    public Boundary {
      Objects.requireNonNull(phase, "phase");
    }

    @Override
    public LedgerJournalKind kind() {
      return LedgerJournalKind.PLAN_BOUNDARY;
    }

    @Override
    public @Nullable LedgerAssertionKind detailKind() {
      return null;
    }

    @Override
    public LedgerBoundaryPhase boundaryPhase() {
      return phase;
    }
  }
}
