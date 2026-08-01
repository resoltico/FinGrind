package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Normalizes immutable attestation history facts before validation and resolution. */
final class AttestationRegistryFacts {
  private AttestationRegistryFacts() {}

  static <T> List<T> sorted(List<T> values, Function<T, BigInteger> acceptedOrder) {
    return values.stream()
        .map(value -> Objects.requireNonNull(value, "facts must not contain null"))
        .sorted(Comparator.comparing(acceptedOrder))
        .toList();
  }
}
