package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/** Classifies an untrusted journal without disclosing its sensitive contents. */
final class PublicationTransactionJournalViolation extends IOException {
  private static final long serialVersionUID = 1L;
  private final Kind kind;

  PublicationTransactionJournalViolation(Kind kind, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  PublicationTransactionJournalViolation(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  Kind kind() {
    return kind;
  }

  /** Stable category for recovery callers deciding whether to preserve untrusted residue. */
  enum Kind {
    MALFORMED,
    INTEGRITY,
    NON_CANONICAL
  }
}
