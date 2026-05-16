package dev.erst.fingrind.executor.bookkeeping.policy;

/** Operational seam for tax determination and tax-posting behavior. */
@FunctionalInterface
public interface TaxPolicy {
  /** Returns whether first-class tax components are enabled by this pack. */
  boolean supportsFirstClassTax();
}
