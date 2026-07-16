package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns pair-publication resources until they transfer to the prepared publication. */
final class SqlitePairPublicationPreparationResources implements AutoCloseable {
  private @Nullable HeldLease bookTargetLease;
  private @Nullable HeldLease secretTargetLease;
  private @Nullable SqliteOwnedDestinationReservation bookReservation;
  private @Nullable SqliteOwnedDestinationReservation secretReservation;

  void holdBookTargetLease(HeldLease lease) {
    bookTargetLease = requireUnsetAndNonNull(bookTargetLease, lease, "bookTargetLease");
  }

  void holdSecretTargetLease(HeldLease lease) {
    secretTargetLease = requireUnsetAndNonNull(secretTargetLease, lease, "secretTargetLease");
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
        requireAndTakeSecretReservation(),
        requireAndTakeBookTargetLease(),
        requireAndTakeSecretTargetLease());
  }

  @Override
  public void close() {
    @Nullable HeldLease closingSecretTargetLease = takeSecretTargetLease();
    @Nullable HeldLease closingBookTargetLease = takeBookTargetLease();
    @Nullable SqliteOwnedDestinationReservation closingSecretReservation = takeSecretReservation();
    @Nullable SqliteOwnedDestinationReservation closingBookReservation = takeBookReservation();
    // Resources close in reverse declaration order: book, secret, book lease, then secret lease.
    try (HeldLease ignoredSecretLease = closingSecretTargetLease;
        HeldLease ignoredBookLease = closingBookTargetLease;
        SqliteOwnedDestinationReservation ignoredSecretReservation = closingSecretReservation;
        SqliteOwnedDestinationReservation ignoredBookReservation = closingBookReservation) {
      // Closing releases only resources that were not transferred to the prepared publication.
    }
  }

  private HeldLease requireAndTakeBookTargetLease() {
    return Objects.requireNonNull(takeBookTargetLease(), "bookTargetLease");
  }

  private HeldLease requireAndTakeSecretTargetLease() {
    return Objects.requireNonNull(takeSecretTargetLease(), "secretTargetLease");
  }

  private SqliteOwnedDestinationReservation requireAndTakeSecretReservation() {
    return Objects.requireNonNull(takeSecretReservation(), "secretReservation");
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

  private @Nullable SqliteOwnedDestinationReservation takeSecretReservation() {
    SqliteOwnedDestinationReservation reservation = secretReservation;
    secretReservation = null;
    return reservation;
  }

  private static <T> T requireUnsetAndNonNull(@Nullable T existing, T value, String name) {
    if (existing != null) {
      throw new IllegalStateException(name + " is already owned.");
    }
    return Objects.requireNonNull(value, name);
  }
}
