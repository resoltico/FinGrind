package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RequestProvenance}. */
@NullUnmarked
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
            Optional.of(new CorrelationId(" corr-1 ")));

    assertEquals("actor-1", requestProvenance.actorId().value());
    assertEquals("command-1", requestProvenance.commandId().value());
    assertEquals("idem-1", requestProvenance.idempotencyKey().value());
    assertEquals("cause-1", requestProvenance.causationId().value());
    assertEquals(Optional.of(new CorrelationId("corr-1")), requestProvenance.correlationId());
  }

  @Test
  void constructor_rejectsNullOptionalFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.HUMAN,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                nullCorrelationIdOptional()));
  }

  @Test
  void constructor_rejectsNullActorId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RequestProvenance(
                null,
                ActorType.HUMAN,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.empty()));
  }

  private static Optional<CorrelationId> nullCorrelationIdOptional() {
    return null;
  }
}
