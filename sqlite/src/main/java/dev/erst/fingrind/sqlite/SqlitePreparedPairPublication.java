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
  private SqlitePublicationCapabilityWitness.@Nullable Set capabilityWitnesses;
  private @Nullable SqliteOwnedDestinationReservation bookReservation;
  private @Nullable SqliteOwnedDestinationReservation secretReservation;
  private boolean closed;

  SqlitePreparedPairPublication(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      SqliteOwnedDestinationReservation secretReservation,
      HeldLease bookTargetLease,
      HeldLease secretTargetLease,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.bookTargetPolicy = Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    this.bookReservation = bookReservation;
    this.secretReservation = Objects.requireNonNull(secretReservation, "secretReservation");
    this.bookTargetLease = Objects.requireNonNull(bookTargetLease, "bookTargetLease");
    this.secretTargetLease = Objects.requireNonNull(secretTargetLease, "secretTargetLease");
    this.capabilityWitnesses = Objects.requireNonNull(capabilityWitnesses, "capabilityWitnesses");
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
    requireOpen();
    return bookReservation == null
        ? SqliteOwnedStagedArtifact.create(bookTargetPath, infix, suffix)
        : bookReservation.createStage(infix, suffix);
  }

  SqliteOwnedStagedArtifact createSecretStage(String infix, String suffix) {
    requireOpen();
    return requiredSecretReservation().createStage(infix, suffix);
  }

  PublicationReservations transferReservations() {
    requireOpen();
    SqliteOwnedDestinationReservation transferredSecretReservation = requiredSecretReservation();
    @Nullable SqliteOwnedDestinationReservation transferredBookReservation = bookReservation;
    secretReservation = null;
    bookReservation = null;
    return new PublicationReservations(
        transferredBookReservation,
        transferredSecretReservation,
        requireAndTakeCapabilityWitnesses());
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    @Nullable SqliteOwnedDestinationReservation closingBookReservation = bookReservation;
    @Nullable SqliteOwnedDestinationReservation closingSecretReservation = secretReservation;
    SqlitePublicationCapabilityWitness.@Nullable Set closingCapabilityWitnesses =
        capabilityWitnesses;
    bookReservation = null;
    secretReservation = null;
    capabilityWitnesses = null;
    // Resources close in reverse declaration order: book, secret, book lease, then secret lease.
    try (HeldLease ignoredSecretLease = secretTargetLease;
        HeldLease ignoredBookLease = bookTargetLease;
        SqlitePublicationCapabilityWitness.Set ignoredCapabilityWitnesses =
            closingCapabilityWitnesses;
        SqliteOwnedDestinationReservation ignoredSecretReservation = closingSecretReservation;
        SqliteOwnedDestinationReservation ignoredBookReservation = closingBookReservation) {
      // Closing releases any reservations that were not transferred to a staged pair.
    }
  }

  private SqliteOwnedDestinationReservation requiredSecretReservation() {
    return Objects.requireNonNull(secretReservation, "secretReservation");
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException(
          "The FinGrind prepared protected-book pair publication is already closed.");
    }
  }

  private SqlitePublicationCapabilityWitness.Set requireAndTakeCapabilityWitnesses() {
    SqlitePublicationCapabilityWitness.Set witnesses = capabilityWitnesses;
    capabilityWitnesses = null;
    return Objects.requireNonNull(witnesses, "capabilityWitnesses");
  }

  record PublicationReservations(
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      SqliteOwnedDestinationReservation secretReservation,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    PublicationReservations {
      Objects.requireNonNull(secretReservation, "secretReservation");
      Objects.requireNonNull(capabilityWitnesses, "capabilityWitnesses");
    }
  }
}
