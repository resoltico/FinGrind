package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns destination reservations before source verification transfers them to one staged pair. */
final class SqlitePreparedPairPublication implements PreparedPairPublication {
  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private final RestoredBookTargetPolicy bookTargetPolicy;
  private final HeldLease bookTargetLease;
  private final HeldLease secretTargetLease;
  private @Nullable SqliteOwnedDestinationReservation bookReservation;
  private @Nullable SqliteOwnedDestinationReservation secretReservation;

  SqlitePreparedPairPublication(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      SqliteOwnedDestinationReservation secretReservation,
      HeldLease bookTargetLease,
      HeldLease secretTargetLease) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.bookTargetPolicy = Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    this.bookReservation = bookReservation;
    this.secretReservation = Objects.requireNonNull(secretReservation, "secretReservation");
    this.bookTargetLease = Objects.requireNonNull(bookTargetLease, "bookTargetLease");
    this.secretTargetLease = Objects.requireNonNull(secretTargetLease, "secretTargetLease");
  }

  @Override
  public Path bookTargetPath() {
    return bookTargetPath;
  }

  @Override
  public Path secretTargetPath() {
    return secretTargetPath;
  }

  @Override
  public RestoredBookTargetPolicy bookTargetPolicy() {
    return bookTargetPolicy;
  }

  SqliteOwnedStagedArtifact createBookStage(String infix, String suffix) {
    return bookReservation == null
        ? SqliteOwnedStagedArtifact.create(bookTargetPath, infix, suffix)
        : bookReservation.createStage(infix, suffix);
  }

  SqliteOwnedStagedArtifact createSecretStage(String infix, String suffix) {
    return requiredSecretReservation().createStage(infix, suffix);
  }

  PublicationReservations transferReservations() {
    SqliteOwnedDestinationReservation transferredSecretReservation = requiredSecretReservation();
    @Nullable SqliteOwnedDestinationReservation transferredBookReservation = bookReservation;
    secretReservation = null;
    bookReservation = null;
    return new PublicationReservations(transferredBookReservation, transferredSecretReservation);
  }

  @Override
  public void close() {
    @Nullable SqliteOwnedDestinationReservation closingBookReservation = bookReservation;
    @Nullable SqliteOwnedDestinationReservation closingSecretReservation = secretReservation;
    bookReservation = null;
    secretReservation = null;
    // Resources close in reverse declaration order: book, secret, book lease, then secret lease.
    try (HeldLease ignoredSecretLease = secretTargetLease;
        HeldLease ignoredBookLease = bookTargetLease;
        SqliteOwnedDestinationReservation ignoredSecretReservation = closingSecretReservation;
        SqliteOwnedDestinationReservation ignoredBookReservation = closingBookReservation) {
      // Closing releases any reservations that were not transferred to a staged pair.
    }
  }

  private SqliteOwnedDestinationReservation requiredSecretReservation() {
    return Objects.requireNonNull(secretReservation, "secretReservation");
  }

  record PublicationReservations(
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      SqliteOwnedDestinationReservation secretReservation) {
    PublicationReservations {
      Objects.requireNonNull(secretReservation, "secretReservation");
    }
  }
}
