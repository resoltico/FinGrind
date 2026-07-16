package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Publishes the staged restored-book pair according to its explicit destination policy. */
final class SqliteRestoredBookPairPublication {
  /** Filesystem operations used to publish one verified restored-book pair. */
  record Operators(
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator bookFileLinkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover bookMover) {
    Operators {
      Objects.requireNonNull(bookKeyLinkCreator, "bookKeyLinkCreator");
      Objects.requireNonNull(bookFileLinkCreator, "bookFileLinkCreator");
      Objects.requireNonNull(bookMover, "bookMover");
    }
  }

  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private final RestoredBookTargetPolicy targetPolicy;
  private final Operators operators;
  private final @Nullable SqliteOwnedDestinationReservation bookReservation;
  private final @Nullable SqliteOwnedDestinationReservation secretReservation;

  SqliteRestoredBookPairPublication(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy targetPolicy,
      Operators operators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation secretReservation) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.targetPolicy = Objects.requireNonNull(targetPolicy, "targetPolicy");
    this.operators = Objects.requireNonNull(operators, "operators");
    this.bookReservation = bookReservation;
    this.secretReservation = secretReservation;
  }

  static Operators defaultOperators() {
    return new Operators(
        Files::createLink, Files::createLink, SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  Path bookTargetPath() {
    return bookTargetPath;
  }

  Path secretTargetPath() {
    return secretTargetPath;
  }

  void publishSecret(SqliteOwnedStagedArtifact stagedSecret) throws IOException {
    if (secretReservation == null) {
      SqliteGeneratedSecretTarget.requireAbsent(secretTargetPath)
          .publishRetainingStage(stagedSecret.stagedPath(), operators.bookKeyLinkCreator());
      return;
    }
    try {
      secretReservation.publishRetainingStage(stagedSecret, operators.bookKeyLinkCreator());
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(secretTargetPath, exception);
    }
  }

  void publishBook(SqliteOwnedStagedArtifact stagedBook) throws IOException {
    if (bookReservation != null) {
      bookReservation.publishRetainingStage(stagedBook, operators.bookFileLinkCreator());
      return;
    }
    if (targetPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
      operators.bookMover().move(stagedBook.stagedPath(), bookTargetPath);
      return;
    }
    SqliteProtectedBookPublicationSupport.publishRetainingStage(
        stagedBook.stagedPath(), bookTargetPath, operators.bookFileLinkCreator());
  }

  void closeReservations() {
    // Resources close in reverse declaration order: book before its paired secret.
    try (SqliteOwnedDestinationReservation ignoredSecret = secretReservation;
        SqliteOwnedDestinationReservation ignoredBook = bookReservation) {
      // Closing the resources releases the owned destination markers.
    }
  }
}
