package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns pair-publication resources until they transfer to the prepared publication. */
final class SqlitePairPublicationPreparationResources implements AutoCloseable {
  private @Nullable HeldLease bookTargetLease;
  private @Nullable HeldLease secretTargetLease;
  private SqlitePublicationCapabilityWitness.@Nullable Set capabilityWitnesses;
  private @Nullable SqliteOwnedDestinationReservation bookReservation;
  private @Nullable SqliteOwnedDestinationReservation secretReservation;

  void holdBookTargetLease(HeldLease lease) {
    bookTargetLease = requireUnsetAndNonNull(bookTargetLease, lease, "bookTargetLease");
  }

  void holdSecretTargetLease(HeldLease lease) {
    secretTargetLease = requireUnsetAndNonNull(secretTargetLease, lease, "secretTargetLease");
  }

  void holdCapabilityWitnesses(SqlitePublicationCapabilityWitness.Set witnesses) {
    capabilityWitnesses =
        requireUnsetAndNonNull(capabilityWitnesses, witnesses, "capabilityWitnesses");
  }

  void holdBookReservation(SqliteOwnedDestinationReservation reservation) {
    bookReservation = requireUnsetAndNonNull(bookReservation, reservation, "bookReservation");
  }

  void holdSecretReservation(SqliteOwnedDestinationReservation reservation) {
    secretReservation = requireUnsetAndNonNull(secretReservation, reservation, "secretReservation");
  }

  SqlitePreparedPairPublication transferToPreparedPublication(
      Path bookTargetPath, Path secretTargetPath, RestoredBookTargetPolicy bookTargetPolicy) {
    return new SqlitePreparedPairPublication(
        bookTargetPath,
        secretTargetPath,
        bookTargetPolicy,
        takeBookReservation(),
        Objects.requireNonNull(takeSecretReservation(), "secretReservation"),
        Objects.requireNonNull(takeBookTargetLease(), "bookTargetLease"),
        Objects.requireNonNull(takeSecretTargetLease(), "secretTargetLease"),
        Objects.requireNonNull(takeCapabilityWitnesses(), "capabilityWitnesses"));
  }

  @Override
  public void close() {
    @Nullable HeldLease closingSecretTargetLease = takeSecretTargetLease();
    @Nullable HeldLease closingBookTargetLease = takeBookTargetLease();
    SqlitePublicationCapabilityWitness.@Nullable Set closingCapabilityWitnesses =
        takeCapabilityWitnesses();
    @Nullable SqliteOwnedDestinationReservation closingSecretReservation = takeSecretReservation();
    @Nullable SqliteOwnedDestinationReservation closingBookReservation = takeBookReservation();
    // Resources close in reverse declaration order: book, secret, witnesses, book lease, secret
    // lease. Only resources not transferred to the prepared publication are released here.
    SqliteRuntimeCloseSequence.closeAll(
        List.of(
            () -> closeReservation(closingBookReservation),
            () -> closeReservation(closingSecretReservation),
            () -> closeWitnesses(closingCapabilityWitnesses),
            () -> closeLease(closingBookTargetLease),
            () -> closeLease(closingSecretTargetLease)));
  }

  private @Nullable HeldLease takeBookTargetLease() {
    HeldLease lease = bookTargetLease;
    bookTargetLease = null;
    return lease;
  }

  private @Nullable HeldLease takeSecretTargetLease() {
    HeldLease lease = secretTargetLease;
    secretTargetLease = null;
    return lease;
  }

  private @Nullable SqliteOwnedDestinationReservation takeBookReservation() {
    SqliteOwnedDestinationReservation reservation = bookReservation;
    bookReservation = null;
    return reservation;
  }

  private SqlitePublicationCapabilityWitness.@Nullable Set takeCapabilityWitnesses() {
    SqlitePublicationCapabilityWitness.Set witnesses = capabilityWitnesses;
    capabilityWitnesses = null;
    return witnesses;
  }

  private @Nullable SqliteOwnedDestinationReservation takeSecretReservation() {
    SqliteOwnedDestinationReservation reservation = secretReservation;
    secretReservation = null;
    return reservation;
  }

  private static void closeReservation(@Nullable SqliteOwnedDestinationReservation reservation) {
    if (reservation != null) {
      reservation.close();
    }
  }

  private static void closeWitnesses(SqlitePublicationCapabilityWitness.@Nullable Set witnesses) {
    if (witnesses != null) {
      witnesses.close();
    }
  }

  private static void closeLease(@Nullable HeldLease lease) {
    if (lease != null) {
      lease.close();
    }
  }

  private static <T> T requireUnsetAndNonNull(@Nullable T existing, T value, String name) {
    if (existing != null) {
      throw new IllegalStateException(name + " is already owned.");
    }
    return Objects.requireNonNull(value, name);
  }
}
