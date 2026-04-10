package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RequestProvenance}. */
class RequestProvenanceTest {
  @Test
  void constructor_acceptsNormalizedBoundaryValues() {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(" actor-1 "),
            ActorType.AGENT,
            new CommandId(" command-1 "),
            new IdempotencyKey(" idem-1 "),
            new CausationId(" cause-1 "),
            Optional.of(new CorrelationId(" corr-1 ")),
            Optional.of(new CorrectionReason("  operator correction  ")));

    assertEquals("actor-1", requestProvenance.actorId().value());
    assertEquals("command-1", requestProvenance.commandId().value());
    assertEquals("idem-1", requestProvenance.idempotencyKey().value());
    assertEquals("cause-1", requestProvenance.causationId().value());
    assertEquals(Optional.of(new CorrelationId("corr-1")), requestProvenance.correlationId());
    assertEquals(
        Optional.of(new CorrectionReason("operator correction")), requestProvenance.reason());
  }

  @Test
  @SuppressWarnings("NullOptional")
  void constructor_allowsNullOptionalFields() {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.USER,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            null,
            null);

    assertEquals(Optional.empty(), requestProvenance.correlationId());
    assertFalse(requestProvenance.reason().isPresent());
  }

  @Test
  void constructor_rejectsNullActorId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RequestProvenance(
                null,
                ActorType.USER,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.empty(),
                Optional.empty()));
  }
}
