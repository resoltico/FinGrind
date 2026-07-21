package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RequestProvenance}. */
class RequestProvenanceTest {
  @Test
  void constructor_acceptsNormalizedBoundaryValues() {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new CommandId(" command-1 "),
            new IdempotencyKey(" idem-1 "),
            new CausationId(" cause-1 "),
            Optional.of(new CorrelationId(" corr-1 ")));
    assertEquals("command-1", requestProvenance.commandId().value());
    assertEquals("idem-1", requestProvenance.idempotencyKey().value());
    assertEquals("cause-1", requestProvenance.causationId().value());
    assertEquals(Optional.of(new CorrelationId("corr-1")), requestProvenance.correlationId());
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructor_rejectsNullOptionalFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RequestProvenance(
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                nullCorrelationIdOptional()));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructor_rejectsNullCommandId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RequestProvenance(
                null, new IdempotencyKey("idem-1"), new CausationId("cause-1"), Optional.empty()));
  }

  @org.jspecify.annotations.NullUnmarked
  private static Optional<CorrelationId> nullCorrelationIdOptional() {
    return null;
  }
}
