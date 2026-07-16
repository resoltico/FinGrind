package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Constructs restored-book staging pairs from one named book-and-secret artifact pair. */
final class SqliteStagedRestoredBookPairFactory {
  private SqliteStagedRestoredBookPairFactory() {}

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    return create(
        artifacts,
        targetPolicy,
        restoredPassphrase,
        verificationSupport,
        SqliteRestoredBookPairPublication.defaultOperators(),
        null,
        null);
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators) {
    return create(
        artifacts,
        targetPolicy,
        restoredPassphrase,
        verificationSupport,
        publicationOperators,
        null,
        null);
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation) {
    return create(
        artifacts,
        targetPolicy,
        ownedPassphraseBytes(restoredPassphrase),
        verificationSupport,
        publicationOperators,
        bookReservation,
        bookKeyReservation);
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators) {
    return create(
        artifacts,
        targetPolicy,
        restoredPassphraseBytes,
        verificationSupport,
        publicationOperators,
        null,
        null);
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    return new SqliteStagedRestoredBookPair(
        checkedArtifacts,
        restoredPassphraseBytes,
        verificationSupport,
        new SqliteRestoredBookPairPublication(
            checkedArtifacts.bookTargetPath(),
            checkedArtifacts.secretTargetPath(),
            targetPolicy,
            publicationOperators,
            bookReservation,
            bookKeyReservation));
  }

  private static byte[] ownedPassphraseBytes(SqliteBookPassphrase passphrase) {
    try (SqliteBookPassphrase ownedPassphrase =
        Objects.requireNonNull(passphrase, "restoredPassphrase")) {
      return ownedPassphrase.utf8BytesCopy();
    }
  }
}
