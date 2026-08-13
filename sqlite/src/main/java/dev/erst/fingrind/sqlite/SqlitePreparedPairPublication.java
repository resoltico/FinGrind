package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.Objects;

/** Holds the leased final targets and their authenticated journal-owned private stages. */
final class SqlitePreparedPairPublication implements PreparedPairPublication {
  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private final RestoredBookTargetPolicy bookTargetPolicy;
  private final HeldLease bookTargetLease;
  private final HeldLease secretTargetLease;
  private final SqlitePublicationTransactionPair journaledPair;
  private boolean closed;

  /** Holds one journal-reserved pair without a second stage or recovery authority. */
  SqlitePreparedPairPublication(
      SqlitePublicationTransactionPair journaledPair,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      HeldLease bookTargetLease,
      HeldLease secretTargetLease) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.bookTargetPolicy = Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    this.bookTargetLease = Objects.requireNonNull(bookTargetLease, "bookTargetLease");
    this.secretTargetLease = Objects.requireNonNull(secretTargetLease, "secretTargetLease");
    this.journaledPair = Objects.requireNonNull(journaledPair, "journaledPair");
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

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    journaledPair.releaseStageAccess();
    closeTargetLeases(secretTargetLease, bookTargetLease);
  }

  private static void closeTargetLeases(HeldLease secretTargetLease, HeldLease bookTargetLease) {
    try {
      secretTargetLease.close();
    } catch (RuntimeException | Error secretFailure) {
      try {
        bookTargetLease.close();
      } catch (RuntimeException | Error bookFailure) {
        secretFailure.addSuppressed(bookFailure);
      }
      throw secretFailure;
    }
    bookTargetLease.close();
  }

  SqlitePublicationTransactionPair journaledPair() {
    requireOpen();
    return journaledPair;
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException(
          "The FinGrind prepared protected-book pair publication is already closed.");
    }
  }
}
