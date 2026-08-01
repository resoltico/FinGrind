package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
        null,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
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
        null,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return create(
        artifacts,
        targetPolicy,
        ownedPassphraseBytes(restoredPassphrase),
        verificationSupport,
        publicationOperators,
        bookReservation,
        bookKeyReservation,
        directoryForcer);
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
        null,
        SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return create(
        artifacts,
        targetPolicy,
        restoredPassphraseBytes,
        verificationSupport,
        publicationOperators,
        bookReservation,
        bookKeyReservation,
        directoryForcer,
        SqliteOwnedRegularFileAccess::forceFile);
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    return create(
        artifacts,
        targetPolicy,
        restoredPassphraseBytes,
        verificationSupport,
        new PublicationDependencies(
            publicationOperators,
            bookReservation,
            bookKeyReservation,
            directoryForcer,
            recoveryRecordFileForcer,
            acquireCapabilityWitnesses(artifacts, targetPolicy)));
  }

  static SqliteStagedRestoredBookPair create(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      RestoredBookTargetPolicy targetPolicy,
      byte[] restoredPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      PublicationDependencies dependencies) {
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
            dependencies.publicationOperators(),
            dependencies.bookReservation(),
            dependencies.bookKeyReservation(),
            dependencies.capabilityWitnesses()),
        dependencies.directoryForcer(),
        dependencies.recoveryRecordFileForcer());
  }

  /** Dependencies whose resource ownership is transferred into one restored-book publication. */
  record PublicationDependencies(
      SqliteRestoredBookPairPublication.Operators publicationOperators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation bookKeyReservation,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {}

  private static SqlitePublicationCapabilityWitness.Set acquireCapabilityWitnesses(
      SqliteStagedProtectedBookPairArtifacts artifacts, RestoredBookTargetPolicy targetPolicy) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    try {
      List<SqlitePublicationCapabilityWitness.Requirement> requirements = new ArrayList<>();
      requirements.addAll(
          switch (targetPolicy) {
            case REQUIRE_ABSENT ->
                List.of(
                    SqlitePublicationCapabilityWitness.Requirement.noReplace(
                        checkedArtifacts.bookTargetPath()));
            case REPLACE_SELECTED ->
                List.of(
                    SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                        checkedArtifacts.bookTargetPath()),
                    SqlitePublicationCapabilityWitness.Requirement.noReplace(
                        checkedArtifacts.bookTargetPath()));
          });
      requirements.add(
          SqlitePublicationCapabilityWitness.Requirement.noReplace(
              checkedArtifacts.secretTargetPath()));
      return SqlitePublicationCapabilityWitness.acquire(
          requirements, Files::createLink, SqliteProtectedBookPublicationSupport::moveReplacing);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire retained FinGrind restored-book publication capability witnesses.",
          exception);
    }
  }

  private static byte[] ownedPassphraseBytes(SqliteBookPassphrase passphrase) {
    try (SqliteBookPassphrase ownedPassphrase =
        Objects.requireNonNull(passphrase, "restoredPassphrase")) {
      return ownedPassphrase.utf8BytesCopy();
    }
  }
}
