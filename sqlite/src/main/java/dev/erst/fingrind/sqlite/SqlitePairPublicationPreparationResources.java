package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.Objects;

/** Owns final-target leases until they transfer to the authenticated prepared transaction. */
final class SqlitePairPublicationPreparationResources implements AutoCloseable {
  private final SqliteOwnedResourceSlot<HeldLease> bookTargetLease =
      SqliteOwnedResourceSlot.create("bookTargetLease", HeldLease::close);
  private final SqliteOwnedResourceSlot<HeldLease> secretTargetLease =
      SqliteOwnedResourceSlot.create("secretTargetLease", HeldLease::close);

  void holdBookTargetLease(HeldLease lease) {
    bookTargetLease.hold(lease);
  }

  void holdSecretTargetLease(HeldLease lease) {
    secretTargetLease.hold(lease);
  }

  /**
   * Transfers only held target leases to a pair whose transaction has already reserved both stages.
   */
  SqlitePreparedPairPublication transferToJournaledPreparedPublication(
      SqlitePublicationTransactionPair journaledPair,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy) {
    SqlitePreparedPairPublication preparedPublication =
        new SqlitePreparedPairPublication(
            Objects.requireNonNull(journaledPair, "journaledPair"),
            Objects.requireNonNull(bookTargetPath, "bookTargetPath"),
            Objects.requireNonNull(secretTargetPath, "secretTargetPath"),
            Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy"),
            bookTargetLease.peekRequired(),
            secretTargetLease.peekRequired());
    bookTargetLease.transferToSuccessor();
    secretTargetLease.transferToSuccessor();
    return preparedPublication;
  }

  @Override
  public void close() {
    secretTargetLease.releaseIfHeld();
    bookTargetLease.releaseIfHeld();
  }
}
