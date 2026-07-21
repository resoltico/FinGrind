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
            new CommandId("e19c63af-3122-34f8-9d99-939b5a07b261"),
            new IdempotencyKey(" idem-1 "),
            new CausationId(" cause-1 "),
            Optional.of(new CorrelationId(" corr-1 ")));
    assertEquals("e19c63af-3122-34f8-9d99-939b5a07b261", requestProvenance.commandId().value());
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
                new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
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
