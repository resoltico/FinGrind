package dev.erst.fingrind.core;

import java.io.IOException;

/**
 * Executes and recovers authenticated publication transactions through their ID-only authority.
 *
 * <p>Production callers use {@link PublicationTransactionPublisher#openCanonical()}; this narrow
 * seam lets higher-level publication adapters prove their mappings without substituting a
 * filesystem stage or recovery pathname.
 */
public interface PublicationTransactionService {
  /** Publishes the complete requested member set under one authenticated transaction journal. */
  PublicationTransactionResult publish(PublicationTransactionRequest request) throws IOException;

  /** Recovers one transaction using only its authenticated canonical-store identifier. */
  PublicationTransactionResult recover(PublicationTransactionId transactionId) throws IOException;
}
