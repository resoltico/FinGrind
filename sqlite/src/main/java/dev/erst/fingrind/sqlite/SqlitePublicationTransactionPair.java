package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Couples one protected-book/key pair to the single journal that owns its private stages.
 *
 * <p>The pair contains no independent stage record, destination reservation, or recovery pathname.
 * A failed producer leaves its authenticated journal in control; this holder deliberately cannot
 * delete or reinterpret the private paths it received from the journal reservation.
 */
final class SqlitePublicationTransactionPair {
  static final String BOOK_MEMBER_ID = "protected-book";
  static final String SECRET_MEMBER_ID = "encrypted-book-key";

  private final PublicationTransactionService transactions;
  private final PublicationTransactionStageReservation reservation;
  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private @Nullable ProtectedBookPairPublication publication;

  private SqlitePublicationTransactionPair(
      PublicationTransactionService transactions,
      PublicationTransactionStageReservation reservation,
      Path bookTargetPath,
      Path secretTargetPath) {
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.reservation = Objects.requireNonNull(reservation, "reservation");
    this.bookTargetPath = normalize(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = normalize(secretTargetPath, "secretTargetPath");
  }

  /** Reserves the journal before either pair producer can materialize a secret-bearing stage. */
  static SqlitePublicationTransactionPair reserve(
      PublicationTransactionService transactions,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy)
      throws IOException {
    PublicationTransactionService checkedTransactions =
        Objects.requireNonNull(transactions, "transactions");
    Path checkedBookTargetPath = normalize(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = normalize(secretTargetPath, "secretTargetPath");
    PublicationTransactionStageReservation reservation =
        checkedTransactions.reserveStages(
            requestFor(checkedBookTargetPath, checkedSecretTargetPath, bookTargetPolicy));
    return new SqlitePublicationTransactionPair(
        checkedTransactions, reservation, checkedBookTargetPath, checkedSecretTargetPath);
  }

  /** Creates the complete reserved-stage plan for one protected-book/key pair transaction. */
  static PublicationTransactionRequest requestFor(
      Path bookTargetPath, Path secretTargetPath, RestoredBookTargetPolicy bookTargetPolicy) {
    Path checkedBookTargetPath = normalize(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = normalize(secretTargetPath, "secretTargetPath");
    PublicationMode bookMode =
        switch (Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy")) {
          case REQUIRE_ABSENT -> PublicationMode.NO_REPLACE_LINK;
          case REPLACE_SELECTED -> PublicationMode.REPLACE;
        };
    return new PublicationTransactionRequest(
        List.of(
            PublicationTransactionMemberRequest.reserveStage(
                BOOK_MEMBER_ID,
                PublicationTransactionMemberRole.PROTECTED_BOOK,
                checkedBookTargetPath,
                bookMode),
            PublicationTransactionMemberRequest.reserveStage(
                SECRET_MEMBER_ID,
                PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                checkedSecretTargetPath,
                PublicationMode.NO_REPLACE_LINK)));
  }

  /** Returns the short-lived private stage destination for the protected-book producer. */
  Path bookStagePath() {
    return reservation.stagePath(BOOK_MEMBER_ID);
  }

  /** Returns the short-lived private stage destination for the generated-key producer. */
  Path secretStagePath() {
    return reservation.stagePath(SECRET_MEMBER_ID);
  }

  /** Returns the sole recovery identifier for this pair publication. */
  dev.erst.fingrind.core.PublicationTransactionId transactionId() {
    return reservation.transactionId();
  }

  /**
   * Authenticates both producer outputs and completes their one journal-owned publication.
   *
   * <p>Only a fully complete transaction can become a pair publication fact. Any other result is
   * reported through the transaction boundary and retains no SQLite cleanup authority.
   */
  ProtectedBookPairPublication publish() throws IOException {
    if (publication != null) {
      return publication;
    }
    PublicationTransactionResult result = transactions.publishReservedStages(reservation);
    if (!result.successful()) {
      throw new IOException("Protected-book pair publication transaction did not complete.");
    }
    publication =
        new ProtectedBookPairPublication(
            new PublicationTransactionArtifact(bookTargetPath, result),
            new PublicationTransactionArtifact(secretTargetPath, result));
    return publication;
  }

  private static Path normalize(Path path, String name) {
    Path normalized = Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    if (normalized.getFileName() == null) {
      throw new IllegalArgumentException(name + " must name an artifact in a parent directory.");
    }
    return normalized;
  }
}
