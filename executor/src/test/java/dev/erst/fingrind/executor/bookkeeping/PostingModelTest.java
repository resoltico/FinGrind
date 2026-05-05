package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for bookkeeping posting-model abstractions. */
class PostingModelTest {
  @Test
  void postingLineageModel_exposesDirectAndReversalMetadata() {
    ReversalReference reversalReference = new ReversalReference(postingId("posting-1"));
    ReversalReason reversalReason = new ReversalReason("refund correction");

    PostingLineageModel direct = PostingLineageModel.direct();
    PostingLineageModel reversal = PostingLineageModel.reversal(reversalReference, reversalReason);

    assertFalse(direct.isReversal());
    assertTrue(direct.reversalReference().isEmpty());
    assertTrue(direct.reversalReason().isEmpty());
    assertTrue(reversal.isReversal());
    assertEquals(Optional.of(reversalReference), reversal.reversalReference());
    assertEquals(Optional.of(reversalReason), reversal.reversalReason());
  }

  @Test
  void postingRequestModel_defaultAccessorsDelegateToLineage() {
    ReversalReference reversalReference = new ReversalReference(postingId("posting-2"));
    ReversalReason reversalReason = new ReversalReason("customer reversal");
    RequestProvenance requestProvenance = requestProvenance("idem-1");
    PostingRequestModel request =
        new PostingRequestModel() {
          @Override
          public JournalEntry journalEntry() {
            return testJournalEntry();
          }

          @Override
          public PostingLineageModel postingLineage() {
            return PostingLineageModel.reversal(reversalReference, reversalReason);
          }

          @Override
          public RequestProvenance requestProvenance() {
            return requestProvenance;
          }
        };

    assertEquals(Optional.of(reversalReference), request.reversalReference());
    assertEquals(Optional.of(reversalReason), request.reversalReason());
    assertEquals(requestProvenance, request.requestProvenance());
  }

  private static dev.erst.fingrind.core.PostingId postingId(String value) {
    return new dev.erst.fingrind.core.PostingId(value);
  }

  private static JournalEntry testJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-05-05"),
        List.of(
            new JournalLine(
                new dev.erst.fingrind.core.AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
            new JournalLine(
                new dev.erst.fingrind.core.AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")))));
  }

  private static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }
}
