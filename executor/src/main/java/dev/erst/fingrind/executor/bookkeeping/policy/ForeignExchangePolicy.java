package dev.erst.fingrind.executor.bookkeeping.policy;

/** Operational seam for foreign-exchange measurement and translation behavior. */
@FunctionalInterface
public interface ForeignExchangePolicy {
  /** Returns whether multi-currency transaction evidence is enabled by this pack. */
  boolean supportsTransactionCurrencies();
}
