package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import java.util.Objects;

/** Closed result family for disclosure-pack reporting. */
public sealed interface DisclosurePackResult
    permits DisclosurePackResult.Computed, DisclosurePackResult.Rejected {
  /** Successful report computation. */
  record Computed(DisclosurePack pack) implements DisclosurePackResult {
    public Computed {
      Objects.requireNonNull(pack, "pack");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BookQueryRejection rejection) implements DisclosurePackResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
