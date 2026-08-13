package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Forces one transaction-owned directory after a publication transaction changes an entry in it.
 */
@FunctionalInterface
interface PublicationTransactionDirectoryDurability {
  /** Persists the supplied directory's prior name mutation before dependent work may proceed. */
  void force(Path directory) throws IOException;

  /** Returns the production cross-platform private-output durability operation. */
  static PublicationTransactionDirectoryDurability production() {
    return PrivateOutputDirectoryDurability::force;
  }
}
