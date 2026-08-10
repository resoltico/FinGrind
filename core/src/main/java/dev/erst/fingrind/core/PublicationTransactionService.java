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

  /**
   * Reserves authenticated private stages before an external producer writes any secret bytes.
   *
   * <p>The returned reservation is an in-process producer capability only. Its transaction ID,
   * rather than a stage pathname, remains the sole recovery authority.
   */
  PublicationTransactionStageReservation reserveStages(PublicationTransactionRequest request)
      throws IOException;

  /**
   * Authenticates the complete producer-written stages and publishes their complete transaction.
   *
   * <p>The reservation is intentionally not a recovery input: implementations must re-read the
   * authenticated journal through its transaction ID before admitting any stage bytes.
   */
  PublicationTransactionResult publishReservedStages(
      PublicationTransactionStageReservation reservation) throws IOException;

  /** Recovers one transaction using only its authenticated canonical-store identifier. */
  PublicationTransactionResult recover(PublicationTransactionId transactionId) throws IOException;

  /**
   * Recovers one transaction and, only after complete success, returns its immutable final members.
   *
   * <p>The transaction identifier is the sole recovery input. The receipt never exposes a staged
   * secret pathname, digest, identity, or cleanup capability.
   */
  PublicationTransactionRecoveryReceipt recoverWithReceipt(PublicationTransactionId transactionId)
      throws IOException;
}
