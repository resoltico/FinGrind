package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Covers the canonical persisted date and UTC-instant text contract. */
class CanonicalTemporalTextTest {
  @Test
  void parseLocalDate_acceptsCanonicalDateAndFormatsRoundTrip() {
    LocalDate localDate = CanonicalTemporalText.parseLocalDate("2026-05-25", "effectiveDate");

    assertEquals(LocalDate.of(2026, 5, 25), localDate);
    assertEquals("2026-05-25", CanonicalTemporalText.formatLocalDate(localDate));
    assertTrue(CanonicalTemporalText.isCanonicalLocalDate("2026-05-25"));
  }

  @Test
  void parseLocalDate_rejectsNonCanonicalOrImpossibleDates() {
    IllegalArgumentException impossibleDate =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseLocalDate("2026-02-30", "effectiveDate"));
    IllegalArgumentException nonCanonicalDate =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseLocalDate("2026-5-2", "effectiveDate"));

    assertEquals(
        "Expected one canonical YYYY-MM-DD local date for effectiveDate.",
        impossibleDate.getMessage());
    assertEquals(
        "Expected one canonical YYYY-MM-DD local date for effectiveDate.",
        nonCanonicalDate.getMessage());
    assertFalse(CanonicalTemporalText.isCanonicalLocalDate("2026-02-30"));
    assertFalse(CanonicalTemporalText.isCanonicalLocalDate("2026-5-2"));
  }

  @Test
  void parseUtcInstant_acceptsCanonicalInstantAndFormatsRoundTrip() {
    Instant instant =
        CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29.123456789Z", "recordedAt");
    Instant millisecondInstant =
        CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29.120Z", "recordedAt");
    Instant microsecondInstant =
        CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29.123400Z", "recordedAt");

    assertEquals(Instant.parse("2026-05-25T06:24:29.123456789Z"), instant);
    assertEquals(Instant.parse("2026-05-25T06:24:29.120Z"), millisecondInstant);
    assertEquals(Instant.parse("2026-05-25T06:24:29.123400Z"), microsecondInstant);
    assertEquals("2026-05-25T06:24:29.123456789Z", CanonicalTemporalText.formatUtcInstant(instant));
    assertEquals(
        "2026-05-25T06:24:29.120Z", CanonicalTemporalText.formatUtcInstant(millisecondInstant));
    assertEquals(
        "2026-05-25T06:24:29.123400Z", CanonicalTemporalText.formatUtcInstant(microsecondInstant));
    assertTrue(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.123456789Z"));
    assertTrue(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.120Z"));
    assertTrue(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.123400Z"));
  }

  @Test
  void parseUtcInstant_rejectsNonCanonicalInstants() {
    IllegalArgumentException missingZulu =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29", "recordedAt"));
    IllegalArgumentException impossibleInstantDate =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseUtcInstant("2026-02-30T06:24:29Z", "recordedAt"));
    IllegalArgumentException nonCanonicalFractionLength =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29.1200Z", "recordedAt"));
    IllegalArgumentException shortFraction =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29.12Z", "recordedAt"));
    IllegalArgumentException trailingZeroNanos =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CanonicalTemporalText.parseUtcInstant(
                    "2026-05-25T06:24:29.123000000Z", "recordedAt"));

    assertEquals(
        "Expected one canonical UTC instant for recordedAt in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        missingZulu.getMessage());
    assertEquals(
        "Expected one canonical UTC instant for recordedAt in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        impossibleInstantDate.getMessage());
    assertEquals(
        "Expected one canonical UTC instant for recordedAt in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        nonCanonicalFractionLength.getMessage());
    assertEquals(
        "Expected one canonical UTC instant for recordedAt in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        shortFraction.getMessage());
    assertEquals(
        "Expected one canonical UTC instant for recordedAt in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        trailingZeroNanos.getMessage());
    assertFalse(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29"));
    assertFalse(CanonicalTemporalText.isCanonicalUtcInstant("2026-02-30T06:24:29Z"));
    assertFalse(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.1200Z"));
    assertFalse(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.12Z"));
    assertFalse(CanonicalTemporalText.isCanonicalUtcInstant("2026-05-25T06:24:29.123000000Z"));
  }

  @Test
  void canonicalTemporalText_rejectsNullInputsAndBlankFieldDescriptions() {
    NullPointerException nullLocalDate =
        assertThrows(
            NullPointerException.class, () -> CanonicalTemporalText.formatLocalDate(nullOf()));
    NullPointerException nullInstant =
        assertThrows(
            NullPointerException.class, () -> CanonicalTemporalText.formatUtcInstant(nullOf()));
    NullPointerException nullText =
        assertThrows(
            NullPointerException.class,
            () -> CanonicalTemporalText.parseLocalDate(nullOf(), "effectiveDate"));
    IllegalArgumentException blankFieldDescription =
        assertThrows(
            IllegalArgumentException.class,
            () -> CanonicalTemporalText.parseUtcInstant("2026-05-25T06:24:29Z", "   "));

    assertEquals("localDate", nullLocalDate.getMessage());
    assertEquals("instant", nullInstant.getMessage());
    assertEquals("effectiveDate text", nullText.getMessage());
    assertEquals("fieldDescription must not be blank.", blankFieldDescription.getMessage());
  }
}
