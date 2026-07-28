package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Direct invariant contracts for retained pair-publication reconciliation facts. */
class SqlitePairPublicationReconciliationTest {
  private static final Path BOOK = Path.of("book.sqlite").toAbsolutePath();
  private static final Path SECRET = Path.of("book.key").toAbsolutePath();

  @Test
  void evidenceBlockedRequiresBothMemberFactsToRemainUnestablished() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqlitePairPublicationReconciliationEvidenceBlocked(
                BOOK,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                SECRET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqlitePairPublicationReconciliationEvidenceBlocked(
                BOOK,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                SECRET,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                null));
  }

  @Test
  void completionUncertaintyRequiresEstablishedPublicationFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqlitePairPublicationReconciliationCompletionUncertain(
                BOOK,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                SECRET,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqlitePairPublicationReconciliationCompletionUncertain(
                BOOK,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                SECRET,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqlitePairPublicationReconciliationCompletionUncertain(
                BOOK,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                SECRET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                null));
    assertDoesNotThrow(
        () ->
            new SqlitePairPublicationReconciliationCompletionUncertain(
                BOOK,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                SECRET,
                ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                null));
  }
}
