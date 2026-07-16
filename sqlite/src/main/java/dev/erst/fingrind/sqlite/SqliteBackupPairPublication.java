package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns no-replace publication and cleanup for one staged backup pair. */
final class SqliteBackupPairPublication {
  private final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator;
  private final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookLinkCreator;
  private final @Nullable SqliteOwnedDestinationReservation bookReservation;
  private final @Nullable SqliteOwnedDestinationReservation keyReservation;

  SqliteBackupPairPublication(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookLinkCreator,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation keyReservation) {
    this.keyLinkCreator = Objects.requireNonNull(keyLinkCreator, "keyLinkCreator");
    this.bookLinkCreator = Objects.requireNonNull(bookLinkCreator, "bookLinkCreator");
    this.bookReservation = bookReservation;
    this.keyReservation = keyReservation;
  }

  void publishKey(SqliteOwnedStagedArtifact stagedKey, Path finalKeyPath, Path occupiedKeyPath)
      throws IOException {
    if (keyReservation == null) {
      SqliteGeneratedSecretTarget.requireAbsent(finalKeyPath)
          .publishRetainingStage(stagedKey.stagedPath(), keyLinkCreator);
      return;
    }
    try {
      keyReservation.publishRetainingStage(stagedKey, keyLinkCreator);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(occupiedKeyPath, exception);
    }
  }

  void publishBook(SqliteOwnedStagedArtifact stagedBook, Path finalBookPath) throws IOException {
    if (bookReservation == null) {
      SqliteProtectedBookPublicationSupport.publishRetainingStage(
          stagedBook.stagedPath(), finalBookPath, bookLinkCreator);
      return;
    }
    bookReservation.publishRetainingStage(stagedBook, bookLinkCreator);
  }

  void closeReservations() {
    // Resources close in reverse declaration order: book before its paired key.
    try (SqliteOwnedDestinationReservation ignoredKey = keyReservation;
        SqliteOwnedDestinationReservation ignoredBook = bookReservation) {
      // Closing the resources releases the owned destination markers.
    }
  }
}
