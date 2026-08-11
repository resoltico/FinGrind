package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionMemberArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  private boolean stageAccessReleased;

  private SqlitePublicationTransactionPair(
      PublicationTransactionService transactions,
      PublicationTransactionStageReservation reservation,
      Path bookTargetPath,
      Path secretTargetPath) {
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.reservation = Objects.requireNonNull(reservation, "reservation");
    this.bookTargetPath = normalize(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = normalize(secretTargetPath, "secretTargetPath");
    SqliteJournaledStageAccess.retain(reservation.stagePath(BOOK_MEMBER_ID), this.bookTargetPath);
    SqliteJournaledStageAccess.retain(
        reservation.stagePath(SECRET_MEMBER_ID), this.secretTargetPath);
  }

  /** Reserves the journal before either pair producer can materialize a secret-bearing stage. */
  static SqlitePublicationTransactionPair reserve(
      PublicationTransactionService transactions,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      PublicationTransactionOwnerContext ownerContext)
      throws IOException {
    PublicationTransactionService checkedTransactions =
        Objects.requireNonNull(transactions, "transactions");
    Path checkedBookTargetPath = normalize(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = normalize(secretTargetPath, "secretTargetPath");
    PublicationTransactionStageReservation reservation =
        checkedTransactions.reserveStages(
            requestFor(
                checkedBookTargetPath, checkedSecretTargetPath, bookTargetPolicy, ownerContext));
    return new SqlitePublicationTransactionPair(
        checkedTransactions, reservation, checkedBookTargetPath, checkedSecretTargetPath);
  }

  /** Creates the complete reserved-stage plan for one protected-book/key pair transaction. */
  static PublicationTransactionRequest requestFor(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      PublicationTransactionOwnerContext ownerContext) {
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
                PublicationMode.NO_REPLACE_LINK)),
        Optional.of(Objects.requireNonNull(ownerContext, "ownerContext")));
  }

  /** Returns the short-lived private stage destination for the protected-book producer. */
  Path bookStagePath() {
    return reservation.stagePath(BOOK_MEMBER_ID);
  }

  /** Returns the short-lived private stage destination for the generated-key producer. */
  Path secretStagePath() {
    return reservation.stagePath(SECRET_MEMBER_ID);
  }

  /** Returns the exact final protected-book target covered by this transaction. */
  Path bookTargetPath() {
    return bookTargetPath;
  }

  /** Returns the exact final generated-secret target covered by this transaction. */
  Path secretTargetPath() {
    return secretTargetPath;
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
    try {
      PublicationTransactionResult result = transactions.publishReservedStages(reservation);
      if (!result.successful()) {
        throw new PublicationTransactionExecutionException(
            result,
            new IOException("Protected-book pair publication transaction did not complete."));
      }
      publication =
          new ProtectedBookPairPublication(
              new PublicationTransactionArtifact(bookTargetPath, result),
              new PublicationTransactionArtifact(secretTargetPath, result));
      return publication;
    } catch (FileAlreadyExistsException collision) {
      PublicationTransactionResult result = transactions.recover(transactionId());
      throw new PublicationTransactionExecutionException(result, collision);
    } finally {
      releaseStageAccess();
    }
  }

  /** Creates the only public-safe response after a journal-owned pair producer cannot finish. */
  ContractFailureException incompleteFailure(
      Path candidateArtifactPath, String argument, Throwable producerFailure) {
    try {
      PublicationTransactionResult result = transactions.recover(transactionId());
      ContractFailureException failure =
          new ContractFailureException(
              ContractErrors.publicationTransactionIncompleteFailure(
                  Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath"),
                  result,
                  Objects.requireNonNull(argument, "argument")));
      failure.initCause(Objects.requireNonNull(producerFailure, "producerFailure"));
      return failure;
    } catch (IOException recoveryFailure) {
      IllegalStateException failure =
          new IllegalStateException(
              "Failed to record the interrupted protected-book publication transaction.",
              recoveryFailure);
      failure.addSuppressed(Objects.requireNonNull(producerFailure, "producerFailure"));
      throw failure;
    } finally {
      releaseStageAccess();
    }
  }

  /** Releases this transient native-access bridge without changing journal-owned residue. */
  void releaseStageAccess() {
    if (stageAccessReleased) {
      return;
    }
    stageAccessReleased = true;
    SqliteJournaledStageAccess.release(bookStagePath());
    SqliteJournaledStageAccess.release(secretStagePath());
  }

  /** Recovers this exact pair by transaction ID and accepts only its expected final members. */
  static @Nullable ProtectedBookPairPublication recoverCompleted(
      PublicationTransactionService transactions,
      dev.erst.fingrind.core.PublicationTransactionId transactionId,
      Path bookTargetPath,
      Path secretTargetPath)
      throws IOException {
    PublicationTransactionRecoveryReceipt receipt =
        Objects.requireNonNull(transactions, "transactions")
            .recoverWithReceipt(Objects.requireNonNull(transactionId, "transactionId"));
    return recoverCompleted(receipt, bookTargetPath, secretTargetPath);
  }

  /** Accepts an already-recovered receipt only when it proves this exact protected-book pair. */
  static @Nullable ProtectedBookPairPublication recoverCompleted(
      PublicationTransactionRecoveryReceipt receipt, Path bookTargetPath, Path secretTargetPath)
      throws IOException {
    PublicationTransactionRecoveryReceipt checkedReceipt =
        Objects.requireNonNull(receipt, "receipt");
    if (!checkedReceipt.transactionResult().successful()) {
      return null;
    }
    PublicationTransactionArtifact bookPublication =
        requiredMember(
            checkedReceipt,
            BOOK_MEMBER_ID,
            PublicationTransactionMemberRole.PROTECTED_BOOK,
            bookTargetPath);
    PublicationTransactionArtifact secretPublication =
        requiredMember(
            checkedReceipt,
            SECRET_MEMBER_ID,
            PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
            secretTargetPath);
    if (checkedReceipt.publishedArtifacts().size() != 2) {
      throw new IOException("Protected-book pair transaction reported an unexpected member set.");
    }
    return new ProtectedBookPairPublication(bookPublication, secretPublication);
  }

  private static PublicationTransactionArtifact requiredMember(
      PublicationTransactionRecoveryReceipt receipt,
      String expectedMemberId,
      PublicationTransactionMemberRole expectedRole,
      Path expectedFinalPath)
      throws IOException {
    return receipt.publishedArtifacts().stream()
        .filter(member -> member.memberId().equals(expectedMemberId))
        .findFirst()
        .map(
            member -> {
              requireExpectedMember(member, expectedRole, expectedFinalPath);
              return member.artifact();
            })
        .orElseThrow(
            () ->
                new IOException(
                    "Protected-book pair transaction did not report member "
                        + expectedMemberId
                        + "."));
  }

  private static void requireExpectedMember(
      PublicationTransactionMemberArtifact member,
      PublicationTransactionMemberRole expectedRole,
      Path expectedFinalPath) {
    if (member.role() != expectedRole) {
      throw new IllegalArgumentException(
          "Protected-book pair transaction member has an unexpected artifact role.");
    }
    Path canonicalExpectedFinalPath =
        new PublicationTransactionArtifact(
                Objects.requireNonNull(expectedFinalPath, "expectedFinalPath"),
                member.artifact().transactionResult())
            .publishedArtifactPath();
    if (!member.artifact().publishedArtifactPath().equals(canonicalExpectedFinalPath)) {
      throw new IllegalArgumentException(
          "Protected-book pair transaction member has an unexpected final artifact path.");
    }
  }

  private static Path normalize(Path path, String name) {
    Path normalized = Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    if (normalized.getFileName() == null) {
      throw new IllegalArgumentException(name + " must name an artifact in a parent directory.");
    }
    return normalized;
  }
}
