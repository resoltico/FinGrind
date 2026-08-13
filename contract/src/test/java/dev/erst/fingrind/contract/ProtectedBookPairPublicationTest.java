package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionState;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Proves pair-publication facts expose only final artifacts and one completed transaction. */
class ProtectedBookPairPublicationTest {
  @Test
  void requiresTwoDistinctFinalArtifactsFromOneCompletedTransaction() {
    PublicationTransactionResult result = successful("0123456789abcdef0123456789abcdef");
    Path bookPath = Path.of("protected", "book.sqlite");
    Path secretPath = Path.of("protected", "book.key");

    ProtectedBookPairPublication publication =
        new ProtectedBookPairPublication(
            new PublicationTransactionArtifact(bookPath, result),
            new PublicationTransactionArtifact(secretPath, result));

    assertEquals(result, publication.publicationTransaction());
    assertEquals(
        bookPath.toAbsolutePath().normalize(),
        publication.requireBookPublication(bookPath).publishedArtifactPath());
    assertEquals(
        secretPath.toAbsolutePath().normalize(),
        publication.requireGeneratedSecretPublication(secretPath).publishedArtifactPath());
  }

  @Test
  void rejectsMembersFromDifferentTransactions() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublication(
                new PublicationTransactionArtifact(
                    Path.of("protected", "book.sqlite"),
                    successful("0123456789abcdef0123456789abcdef")),
                new PublicationTransactionArtifact(
                    Path.of("protected", "book.key"),
                    successful("fedcba9876543210fedcba9876543210"))));
  }

  @Test
  void rejectsOnePathForBothMembersAndTransactionProofForAnExternalPair() {
    PublicationTransactionResult result = successful("0123456789abcdef0123456789abcdef");
    Path finalPath = Path.of("protected", "book.sqlite");
    ProtectedBookPairPublication publication =
        new ProtectedBookPairPublication(
            new PublicationTransactionArtifact(finalPath, result),
            new PublicationTransactionArtifact(Path.of("protected", "book.key"), result));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublication(
                new PublicationTransactionArtifact(finalPath, result),
                new PublicationTransactionArtifact(finalPath, result)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ProtectedBookPairPublicationCompletion.requirePublication(
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED, publication));
  }

  private static PublicationTransactionResult successful(String transactionId) {
    return new PublicationTransactionResult(
        new PublicationTransactionId(transactionId),
        PublicationTransactionState.COMPLETE,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE));
  }
}
