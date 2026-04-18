package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommittedProvenance}. */
class CommittedProvenanceTest {
  @Test
  void constructor_keepsCommittedAuditPayload() {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1")));
    CommittedProvenance committedProvenance =
        new CommittedProvenance(
            requestProvenance, Instant.parse("2026-04-07T10:15:30Z"), SourceChannel.CLI);

    assertEquals(requestProvenance, committedProvenance.requestProvenance());
    assertEquals(Instant.parse("2026-04-07T10:15:30Z"), committedProvenance.recordedAt());
    assertEquals(SourceChannel.CLI, committedProvenance.sourceChannel());
  }

  @Test
  void constructor_rejectsNullRecordedAt() {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.USER,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            Optional.empty());

    assertThrows(
        NullPointerException.class,
        () -> new CommittedProvenance(requestProvenance, null, SourceChannel.CLI));
  }
}
