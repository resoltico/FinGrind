package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Owns pair-publication resources until they transfer to the prepared publication. */
final class SqlitePairPublicationPreparationResources implements AutoCloseable {
  private final SqliteOwnedResourceSlot<HeldLease> bookTargetLease =
      SqliteOwnedResourceSlot.create("bookTargetLease", HeldLease::close);
  private final SqliteOwnedResourceSlot<HeldLease> secretTargetLease =
      SqliteOwnedResourceSlot.create("secretTargetLease", HeldLease::close);
  private final SqliteOwnedResourceSlot<SqlitePublicationCapabilityWitness.Set>
      capabilityWitnesses =
          SqliteOwnedResourceSlot.create(
              "capabilityWitnesses", SqlitePublicationCapabilityWitness.Set::close);
  private final SqliteOwnedResourceSlot<SqliteOwnedDestinationReservation> bookReservation =
      SqliteOwnedResourceSlot.create("bookReservation", SqliteOwnedDestinationReservation::close);
  private final SqliteOwnedResourceSlot<SqliteOwnedDestinationReservation> secretReservation =
      SqliteOwnedResourceSlot.create("secretReservation", SqliteOwnedDestinationReservation::close);

  void holdBookTargetLease(HeldLease lease) {
    bookTargetLease.hold(lease);
  }

  void holdSecretTargetLease(HeldLease lease) {
    secretTargetLease.hold(lease);
  }

  void holdCapabilityWitnesses(SqlitePublicationCapabilityWitness.Set witnesses) {
    capabilityWitnesses.hold(witnesses);
  }

  void holdBookReservation(SqliteOwnedDestinationReservation reservation) {
    bookReservation.hold(reservation);
  }

  void holdSecretReservation(SqliteOwnedDestinationReservation reservation) {
    secretReservation.hold(reservation);
  }

  SqlitePreparedPairPublication transferToPreparedPublication(
      Path bookTargetPath, Path secretTargetPath, RestoredBookTargetPolicy bookTargetPolicy) {
    Path checkedBookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    RestoredBookTargetPolicy checkedBookTargetPolicy =
        Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    requireSuccessorConstructionInputs();

    // Construct the successor before surrendering any slot. A missing input or constructor failure
    // leaves every resource here, so this owner's close path can still release the complete set.
    SqlitePreparedPairPublication preparedPublication =
        new SqlitePreparedPairPublication(
            checkedBookTargetPath,
            checkedSecretTargetPath,
            checkedBookTargetPolicy,
            bookReservation.peekNullable(),
            secretReservation.peekRequired(),
            bookTargetLease.peekRequired(),
            secretTargetLease.peekRequired(),
            capabilityWitnesses.peekRequired());
    bookReservation.transferToSuccessor();
    secretReservation.transferToSuccessor();
    bookTargetLease.transferToSuccessor();
    secretTargetLease.transferToSuccessor();
    capabilityWitnesses.transferToSuccessor();
    return preparedPublication;
  }

  private void requireSuccessorConstructionInputs() {
    bookReservation.peekNullable();
    secretReservation.peekRequired();
    bookTargetLease.peekRequired();
    secretTargetLease.peekRequired();
    capabilityWitnesses.peekRequired();
  }

  @Override
  public void close() {
    // Release destination reservations before their witnesses and leases. Only resources not
    // transferred to the prepared publication remain owned here.
    SqliteRuntimeCloseSequence.closeAll(
        List.of(
            bookReservation::releaseIfHeld,
            secretReservation::releaseIfHeld,
            capabilityWitnesses::releaseIfHeld,
            bookTargetLease::releaseIfHeld,
            secretTargetLease::releaseIfHeld));
  }
}
