package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostEntryResult}. */
class PostEntryResultTest {
  @Test
  void preflightAccepted_holdsItsPayload() {
    PostEntryResult.PreflightAccepted result =
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07"));
    assertEquals("idem-1", result.idempotencyKey().value());
  }

  @Test
  void committed_holdsItsPayload() {
    PostEntryResult.Committed result =
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            false);
    assertEquals("posting-1", result.postingId().value());
    assertFalse(result.idempotentReplay());
  }

  @Test
  void preflightRejected_holdsTypedRejection() {
    PostEntryResult.PreflightRejected result =
        new PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.IdempotencyKeyConflict());
    assertEquals(new PostingRejection.IdempotencyKeyConflict(), result.rejection());
  }

  @Test
  void commitRejected_rejectsNullRejection() {
    assertThrows(
        NullPointerException.class,
        () -> new PostEntryResult.CommitRejected(new IdempotencyKey("idem-1"), nullOf()));
  }
}
