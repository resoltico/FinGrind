package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProvenanceEnvelope}. */
class ProvenanceEnvelopeTest {
  @Test
  void constructor_normalizesOptionalFields() {
    ProvenanceEnvelope provenanceEnvelope =
        new ProvenanceEnvelope(
            " actor-1 ",
            ProvenanceEnvelope.ActorType.AGENT,
            " command-1 ",
            new IdempotencyKey(" idem-1 "),
            " cause-1 ",
            Optional.of(" corr-1 "),
            Instant.parse("2026-04-07T10:15:30Z"),
            Optional.of("   "),
            ProvenanceEnvelope.SourceChannel.CLI);

    assertEquals("actor-1", provenanceEnvelope.actorId());
    assertEquals("command-1", provenanceEnvelope.commandId());
    assertEquals("cause-1", provenanceEnvelope.causationId());
    assertEquals(Optional.of("corr-1"), provenanceEnvelope.correlationId());
    assertFalse(provenanceEnvelope.reason().isPresent());
  }

  @Test
  void constructor_allowsNullOptionalFields() {
    ProvenanceEnvelope provenanceEnvelope =
        new ProvenanceEnvelope(
            "actor-1",
            ProvenanceEnvelope.ActorType.USER,
            "command-1",
            new IdempotencyKey("idem-1"),
            "cause-1",
            null,
            Instant.parse("2026-04-07T10:15:30Z"),
            null,
            ProvenanceEnvelope.SourceChannel.API);

    assertEquals(Optional.empty(), provenanceEnvelope.correlationId());
    assertEquals(Optional.empty(), provenanceEnvelope.reason());
  }

  @Test
  void constructor_rejectsBlankActorId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProvenanceEnvelope(
                " ",
                ProvenanceEnvelope.ActorType.USER,
                "command-1",
                new IdempotencyKey("idem-1"),
                "cause-1",
                Optional.empty(),
                Instant.parse("2026-04-07T10:15:30Z"),
                Optional.empty(),
                ProvenanceEnvelope.SourceChannel.TEST));
  }
}
