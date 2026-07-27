package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Owns no-replace publication and non-destructive reservation release for one staged backup pair.
 */
final class SqliteBackupPairPublication {
  private final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator;
  private final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookLinkCreator;
  private final @Nullable SqliteOwnedDestinationReservation bookReservation;
  private final @Nullable SqliteOwnedDestinationReservation keyReservation;
  private final SqlitePublicationCapabilityWitness.Set capabilityWitnesses;

  SqliteBackupPairPublication(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookLinkCreator,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation keyReservation,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    this.keyLinkCreator = Objects.requireNonNull(keyLinkCreator, "keyLinkCreator");
    this.bookLinkCreator = Objects.requireNonNull(bookLinkCreator, "bookLinkCreator");
    this.bookReservation = bookReservation;
    this.keyReservation = keyReservation;
    this.capabilityWitnesses = Objects.requireNonNull(capabilityWitnesses, "capabilityWitnesses");
  }

  void publishKey(
      SqliteOwnedStagedArtifact stagedKey,
      Path finalKeyPath,
      Path occupiedKeyPath,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard publicationGuard,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt attempt)
      throws IOException {
    var guardedLinkCreator =
        SqliteProtectedBookPublicationSupport.guardedLinkCreator(
            SqliteProtectedBookPublicationSupport.FinalMember.SECRET,
            publicationGuard,
            attempt,
            keyLinkCreator);
    if (keyReservation == null) {
      SqliteGeneratedSecretTarget.requireAbsent(finalKeyPath)
          .publishRetainingStage(stagedKey.stagedPath(), guardedLinkCreator);
      return;
    }
    try {
      keyReservation.publishRetainingStage(stagedKey, guardedLinkCreator);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(occupiedKeyPath, exception);
    }
  }

  void publishBook(
      SqliteOwnedStagedArtifact stagedBook,
      Path finalBookPath,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard publicationGuard,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt attempt)
      throws IOException {
    var guardedLinkCreator =
        SqliteProtectedBookPublicationSupport.guardedLinkCreator(
            SqliteProtectedBookPublicationSupport.FinalMember.BOOK,
            publicationGuard,
            attempt,
            bookLinkCreator);
    if (bookReservation == null) {
      SqliteProtectedBookPublicationSupport.publishRetainingStage(
          stagedBook.stagedPath(), finalBookPath, guardedLinkCreator);
      return;
    }
    bookReservation.publishRetainingStage(stagedBook, guardedLinkCreator);
  }

  void closeReservations() {
    // Resources close in reverse declaration order: book before its paired key.
    try (SqliteOwnedDestinationReservation ignoredKey = keyReservation;
        SqliteOwnedDestinationReservation ignoredBook = bookReservation;
        SqlitePublicationCapabilityWitness.Set ignoredCapabilityWitnesses = capabilityWitnesses) {
      // Closing the resources releases the owned destination markers.
    }
  }

  void requireCapabilityCurrent(
      Path targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind)
      throws IOException {
    capabilityWitnesses.requireCurrent(targetPath, primitiveKind);
  }
}
