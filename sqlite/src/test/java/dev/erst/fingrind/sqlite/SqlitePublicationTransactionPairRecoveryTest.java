package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionMemberArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves a recovered pair accepts only the exact completed two-member journal receipt. */
class SqlitePublicationTransactionPairRecoveryTest {
  private static final Path BOOK = Path.of("private", "backup.sqlite");
  private static final Path SECRET = Path.of("private", "backup.key");

  @Test
  void recoversTheExactCompletedPairThroughTheIdOnlyServiceBoundary() throws Exception {
    PublicationTransactionResult complete = completeResult();
    PublicationTransactionRecoveryReceipt receipt = completeReceipt(complete, BOOK, SECRET);

    ProtectedBookPairPublication recovered =
        assertInstanceOf(
            ProtectedBookPairPublication.class,
            SqlitePublicationTransactionPair.recoverCompleted(
                new ReceiptTransactions(receipt), complete.transactionId(), BOOK, SECRET));
    assertEquals(
        BOOK.toAbsolutePath().normalize(),
        recovered.requireBookPublication(BOOK).publishedArtifactPath());
  }

  @Test
  void rejectsIncompleteOrOverbroadJournalReceipts() throws Exception {
    PublicationTransactionResult incomplete =
        result(
            PublicationTransactionState.BLOCKED,
            PublicationCommitOutcome.NONE_COMMITTED,
            PublicationCleanupOutcome.INCOMPLETE);
    assertNull(
        SqlitePublicationTransactionPair.recoverCompleted(
            new PublicationTransactionRecoveryReceipt(incomplete, List.of()), BOOK, SECRET));

    PublicationTransactionResult complete = completeResult();
    PublicationTransactionRecoveryReceipt extraMember =
        new PublicationTransactionRecoveryReceipt(
            complete,
            List.of(
                artifact(SqlitePublicationTransactionPair.BOOK_MEMBER_ID, BOOK, complete),
                artifact(SqlitePublicationTransactionPair.SECRET_MEMBER_ID, SECRET, complete),
                artifact("extra", Path.of("private", "extra"), complete)));
    assertThrows(
        IOException.class,
        () -> SqlitePublicationTransactionPair.recoverCompleted(extraMember, BOOK, SECRET));
  }

  @Test
  void rejectsMissingWrongRoleAndWrongTargetMembers() {
    PublicationTransactionResult complete = completeResult();
    PublicationTransactionRecoveryReceipt missingSecret =
        new PublicationTransactionRecoveryReceipt(
            complete,
            List.of(artifact(SqlitePublicationTransactionPair.BOOK_MEMBER_ID, BOOK, complete)));
    assertThrows(
        IOException.class,
        () -> SqlitePublicationTransactionPair.recoverCompleted(missingSecret, BOOK, SECRET));

    PublicationTransactionRecoveryReceipt wrongRole =
        new PublicationTransactionRecoveryReceipt(
            complete,
            List.of(
                new PublicationTransactionMemberArtifact(
                    SqlitePublicationTransactionPair.BOOK_MEMBER_ID,
                    PublicationTransactionMemberRole.PDF_REPORT,
                    new PublicationTransactionArtifact(BOOK, complete)),
                artifact(SqlitePublicationTransactionPair.SECRET_MEMBER_ID, SECRET, complete)));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqlitePublicationTransactionPair.recoverCompleted(wrongRole, BOOK, SECRET));

    PublicationTransactionRecoveryReceipt wrongTarget =
        completeReceipt(complete, BOOK, Path.of("other.key"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqlitePublicationTransactionPair.recoverCompleted(wrongTarget, BOOK, SECRET));
  }

  private static PublicationTransactionRecoveryReceipt completeReceipt(
      PublicationTransactionResult result, Path book, Path secret) {
    return new PublicationTransactionRecoveryReceipt(
        result,
        List.of(
            artifact(SqlitePublicationTransactionPair.BOOK_MEMBER_ID, book, result),
            artifact(SqlitePublicationTransactionPair.SECRET_MEMBER_ID, secret, result)));
  }

  private static PublicationTransactionMemberArtifact artifact(
      String memberId, Path target, PublicationTransactionResult result) {
    PublicationTransactionMemberRole role =
        SqlitePublicationTransactionPair.BOOK_MEMBER_ID.equals(memberId)
            ? PublicationTransactionMemberRole.PROTECTED_BOOK
            : PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY;
    return new PublicationTransactionMemberArtifact(
        memberId, role, new PublicationTransactionArtifact(target, result));
  }

  private static PublicationTransactionResult completeResult() {
    return result(
        PublicationTransactionState.COMPLETE,
        PublicationCommitOutcome.ALL_COMMITTED,
        PublicationCleanupOutcome.COMPLETE);
  }

  private static PublicationTransactionResult result(
      PublicationTransactionState state,
      PublicationCommitOutcome commit,
      PublicationCleanupOutcome cleanup) {
    return new PublicationTransactionResult(
        new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
        state,
        new PublicationTransactionOutcome(commit, cleanup));
  }

  private record ReceiptTransactions(PublicationTransactionRecoveryReceipt receipt)
      implements PublicationTransactionService {
    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request) {
      throw new AssertionError("Pair receipt recovery must not publish.");
    }

    @Override
    public PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) {
      throw new AssertionError("Pair receipt recovery must not reserve stages.");
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        PublicationTransactionStageReservation reservation) {
      throw new AssertionError("Pair receipt recovery must not publish stages.");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
      throw new AssertionError("Pair receipt recovery reads the immutable receipt directly.");
    }

    @Override
    public PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      return receipt;
    }
  }
}
