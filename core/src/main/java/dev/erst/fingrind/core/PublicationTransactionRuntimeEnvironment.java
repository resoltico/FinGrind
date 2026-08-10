package dev.erst.fingrind.core;

import java.util.Map;

/**
 * Owns the process environment facts used only to locate the canonical transaction journal store.
 */
final class PublicationTransactionRuntimeEnvironment {
  private PublicationTransactionRuntimeEnvironment() {}

  /** Returns the current process facts required for canonical transaction-store resolution. */
  static Facts current() {
    return new Facts(
        System.getProperty("os.name"), System.getenv(), System.getProperty("user.home"));
  }

  /** Immutable process facts supplied to canonical transaction-store resolution. */
  record Facts(String operatingSystemName, Map<String, String> environment, String userHome) {}
}
