package dev.erst.fingrind.application;

import java.time.Instant;
import java.util.Objects;

/** Closed result family for explicit book initialization. */
public sealed interface OpenBookResult permits OpenBookResult.Opened, OpenBookResult.Rejected {

  /** Success result for a newly initialized book. */
  record Opened(Instant initializedAt) implements OpenBookResult {
    /** Validates the initialization timestamp. */
    public Opened {
      Objects.requireNonNull(initializedAt, "initializedAt");
    }
  }

  /** Deterministic refusal for open-book. */
  record Rejected(BookAdministrationRejection rejection) implements OpenBookResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
