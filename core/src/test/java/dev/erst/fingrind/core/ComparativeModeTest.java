package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the stable comparative-mode vocabulary exposed to protocol consumers. */
class ComparativeModeTest {
  @Test
  void wireHelpers_publishStableVocabularyAndRoundTrip() {
    assertEquals(
        List.of("none", "same-period-prior-year", "range"),
        WireValue.wireValues(ComparativeMode.class));
    for (ComparativeMode comparativeMode : ComparativeMode.values()) {
      assertEquals(
          comparativeMode,
          WireValue.fromWireValue(
              ComparativeMode.class, comparativeMode.wireValue(), "Unsupported comparativeMode"));
    }
  }

  @Test
  void wireHelpers_rejectNullAndUnknownValues() {
    assertThrows(
        NullPointerException.class,
        () ->
            WireValue.fromWireValue(
                ComparativeMode.class, nullOf(), "Unsupported comparativeMode"));
    IllegalArgumentException unknownValueFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                WireValue.fromWireValue(
                    ComparativeMode.class,
                    "trailing-twelve-months",
                    "Unsupported comparativeMode"));

    assertEquals(
        "Unsupported comparativeMode: trailing-twelve-months", unknownValueFailure.getMessage());
  }
}
