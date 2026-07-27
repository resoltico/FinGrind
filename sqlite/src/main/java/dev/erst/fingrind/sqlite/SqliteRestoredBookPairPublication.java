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
  private final SqlitePublicationCapabilityWitness.Set capabilityWitnesses;

  SqliteRestoredBookPairPublication(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy targetPolicy,
      Operators operators,
      @Nullable SqliteOwnedDestinationReservation bookReservation,
      @Nullable SqliteOwnedDestinationReservation secretReservation,
      SqlitePublicationCapabilityWitness.Set capabilityWitnesses) {
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.targetPolicy = Objects.requireNonNull(targetPolicy, "targetPolicy");
    this.operators = Objects.requireNonNull(operators, "operators");
    this.bookReservation = bookReservation;
    this.secretReservation = secretReservation;
    this.capabilityWitnesses = Objects.requireNonNull(capabilityWitnesses, "capabilityWitnesses");
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

  RestoredBookTargetPolicy targetPolicy() {
    return targetPolicy;
  }

  void publishSecret(
      SqliteOwnedStagedArtifact stagedSecret,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard publicationGuard,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt attempt)
      throws IOException {
    SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard checkedGuard =
        Objects.requireNonNull(publicationGuard, "publicationGuard");
    SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt checkedAttempt =
        Objects.requireNonNull(attempt, "attempt");
    var guardedLinkCreator =
        SqliteProtectedBookPublicationSupport.guardedLinkCreator(
            SqliteProtectedBookPublicationSupport.FinalMember.SECRET,
            checkedGuard,
            checkedAttempt,
            operators.bookKeyLinkCreator());
    if (secretReservation == null) {
      SqliteGeneratedSecretTarget.requireAbsent(secretTargetPath)
          .publishRetainingStage(stagedSecret.stagedPath(), guardedLinkCreator);
      return;
    }
    try {
      secretReservation.publishRetainingStage(stagedSecret, guardedLinkCreator);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(secretTargetPath, exception);
    }
  }

  void publishBook(
      SqliteOwnedStagedArtifact stagedBook,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard publicationGuard,
      SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt attempt)
      throws IOException {
    SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuard checkedGuard =
        Objects.requireNonNull(publicationGuard, "publicationGuard");
    SqliteProtectedBookPublicationSupport.FinalMemberPublicationAttempt checkedAttempt =
        Objects.requireNonNull(attempt, "attempt");
    var guardedLinkCreator =
        SqliteProtectedBookPublicationSupport.guardedLinkCreator(
            SqliteProtectedBookPublicationSupport.FinalMember.BOOK,
            checkedGuard,
            checkedAttempt,
            operators.bookFileLinkCreator());
    if (bookReservation != null) {
      bookReservation.publishRetainingStage(stagedBook, guardedLinkCreator);
      return;
    }
    if (targetPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
      capabilityWitnesses.requireCurrent(
          bookTargetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Path replacementBridge =
          SqliteProtectedBookPublicationSupport.createReplacementBridgeRetainingStage(
              stagedBook.stagedPath(), bookTargetPath, operators.bookFileLinkCreator());
      SqliteProtectedBookPublicationSupport.requireGuard(
          SqliteProtectedBookPublicationSupport.FinalMember.BOOK, checkedGuard);
      checkedAttempt.markAttempted();
      operators.bookMover().move(replacementBridge, bookTargetPath);
      return;
    }
    SqliteProtectedBookPublicationSupport.publishRetainingStage(
        stagedBook.stagedPath(), bookTargetPath, guardedLinkCreator);
  }

  void closeReservations() {
    // Resources close in reverse declaration order: book before its paired secret.
    try (SqliteOwnedDestinationReservation ignoredSecret = secretReservation;
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
