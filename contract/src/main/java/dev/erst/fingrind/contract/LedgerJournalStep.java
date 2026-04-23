package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical journal-visible step identity for one ledger-plan entry. */
public sealed interface LedgerJournalStep
    permits LedgerJournalStep.Standard, LedgerJournalStep.Assertion {
  /** Returns the top-level journal kind for this step identity. */
  LedgerStepKind kind();

  /** Returns the nested assertion kind when the journal step represents an assertion. */
  @Nullable LedgerAssertionKind detailKind();

  /** Creates one non-assert journal step identity. */
  static LedgerJournalStep standard(LedgerStepKind kind) {
    return new Standard(kind);
  }

  /** Creates one assert journal step identity. */
  static LedgerJournalStep assertion(LedgerAssertionKind detailKind) {
    return new Assertion(detailKind);
  }

  /** Journal step identity for one non-assert plan step. */
  record Standard(LedgerStepKind kind) implements LedgerJournalStep {
    /** Validates one standard journal step identity. */
    public Standard {
      Objects.requireNonNull(kind, "kind");
      if (kind == LedgerStepKind.ASSERT) {
        throw new IllegalArgumentException(
            "Standard journal step identities must not use the assert step kind.");
      }
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
    public LedgerStepKind kind() {
      return LedgerStepKind.ASSERT;
    }
  }
}
