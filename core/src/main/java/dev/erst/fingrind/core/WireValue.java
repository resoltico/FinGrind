package dev.erst.fingrind.core;

/** Stable public wire-value contract for machine-visible FinGrind vocabulary types. */
@FunctionalInterface
public interface WireValue {
  /** Returns the canonical externally visible token for this value. */
  String wireValue();
}
