package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/** Test-only interruption that must leave the preceding durable transaction fact recoverable. */
final class PublicationTransactionInjectedFault extends IOException {
  private static final long serialVersionUID = 1L;

  PublicationTransactionInjectedFault(PublicationTransactionFaultPoint point) {
    super(
        "Injected publication transaction interruption after "
            + Objects.requireNonNull(point, "point")
            + ".");
  }
}
