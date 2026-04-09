package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals("posting-1", result.postingId().value());
  }

  @Test
  void rejected_rejectsBlankMessage() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostEntryResult.Rejected(
                PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
                "   ",
                new IdempotencyKey("idem-1")));
  }
}
