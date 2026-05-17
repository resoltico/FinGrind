package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical identity snapshot for one external counterparty. */
public record Counterparty(
    CounterpartyId counterpartyId,
    CounterpartyKind counterpartyKind,
    CounterpartyName displayName) {
  /** Validates one counterparty snapshot. */
  public Counterparty {
    Objects.requireNonNull(counterpartyId, "counterpartyId");
    Objects.requireNonNull(counterpartyKind, "counterpartyKind");
    Objects.requireNonNull(displayName, "displayName");
  }
}
