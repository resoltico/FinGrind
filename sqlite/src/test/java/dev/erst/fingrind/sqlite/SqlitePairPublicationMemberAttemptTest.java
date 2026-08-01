package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import org.junit.jupiter.api.Test;

/** Verifies the irreversible state transitions recorded around each final-member primitive. */
class SqlitePairPublicationMemberAttemptTest {
  @Test
  void permitsOnlyTheOrderedFinalMemberPublicationTransitions() {
    SqlitePairPublicationMemberAttempt book = new SqlitePairPublicationMemberAttempt();
    SqlitePairPublicationMemberAttempt secret = new SqlitePairPublicationMemberAttempt();

    assertFalse(SqlitePairPublicationMemberAttempt.eitherAttempted(book, secret));
    assertThrows(IllegalStateException.class, book::markPublishedDurabilityUnconfirmed);
    assertThrows(IllegalStateException.class, book::markPublishedDurable);

    book.markAttempted();
    assertEquals(ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, book.state());
    assertTrue(SqlitePairPublicationMemberAttempt.eitherAttempted(book, secret));
    assertThrows(IllegalStateException.class, book::markAttempted);

    book.markPublishedDurabilityUnconfirmed();
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED, book.state());
    book.markPublishedDurable();
    assertEquals(ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE, book.state());
    assertThrows(IllegalStateException.class, book::markPublishedDurable);
  }
}
