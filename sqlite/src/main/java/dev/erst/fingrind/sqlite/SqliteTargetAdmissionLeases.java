package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns the two temporary final-target references held until pair admission completes. */
final class SqliteTargetAdmissionLeases implements AutoCloseable {
  private @Nullable SqliteHeldLease bookTargetLease;
  private @Nullable SqliteHeldLease secretTargetLease;
  private boolean closed;

  SqliteTargetAdmissionLeases(SqliteHeldLease bookTargetLease, SqliteHeldLease secretTargetLease) {
    this.bookTargetLease = Objects.requireNonNull(bookTargetLease, "bookTargetLease");
    this.secretTargetLease = Objects.requireNonNull(secretTargetLease, "secretTargetLease");
  }

  /**
   * Moves the already-admitted exact target references into pair-publication preparation.
   *
   * <p>The workflow scope acquired these references before it inspected or mutated any member. A
   * later pair-admission phase must transfer those same references, rather than attempting a second
   * acquisition that would contend with the scope itself when a source is also a target.
   */
  void transferTo(SqlitePairPublicationPreparationResources resources) {
    if (closed) {
      throw new IllegalStateException("The FinGrind target-admission leases are already closed.");
    }
    SqlitePairPublicationPreparationResources checkedResources =
        Objects.requireNonNull(resources, "resources");
    checkedResources.holdBookTargetLease(
        Objects.requireNonNull(bookTargetLease, "bookTargetLease"));
    bookTargetLease = null;
    checkedResources.holdSecretTargetLease(
        Objects.requireNonNull(secretTargetLease, "secretTargetLease"));
    secretTargetLease = null;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    @Nullable SqliteHeldLease closingSecretTargetLease = secretTargetLease;
    secretTargetLease = null;
    @Nullable SqliteHeldLease closingBookTargetLease = bookTargetLease;
    bookTargetLease = null;
    try (SqliteHeldLease ignoredSecretTargetLease = closingSecretTargetLease;
        SqliteHeldLease ignoredBookTargetLease = closingBookTargetLease) {
      // The two retained target references are released in reverse acquisition order.
    }
  }
}
