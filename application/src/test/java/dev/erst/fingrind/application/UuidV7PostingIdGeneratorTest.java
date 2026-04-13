package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UuidV7PostingIdGenerator}. */
class UuidV7PostingIdGeneratorTest {
  @Test
  void defaultConstructor_generatesVersion7UuidWithVariant2() {
    UUID uuid = UUID.fromString(new UuidV7PostingIdGenerator().nextPostingId().value());

    assertEquals(7, uuid.version());
    assertEquals(2, uuid.variant());
  }

  @Test
  void nextPostingId_generatesVersion7UuidWithVariant2() {
    long timestampMillis = 1_776_000_123_456L;
    UuidV7PostingIdGenerator generator =
        new UuidV7PostingIdGenerator(() -> timestampMillis, new Random(42L));

    UUID uuid = UUID.fromString(generator.nextPostingId().value());

    assertEquals(7, uuid.version());
    assertEquals(2, uuid.variant());
    assertEquals(timestampMillis, uuid.getMostSignificantBits() >>> 16);
  }

  @Test
  void nextPostingId_generatesDistinctValuesAcrossCalls() {
    UuidV7PostingIdGenerator generator =
        new UuidV7PostingIdGenerator(() -> 1_776_000_123_456L, new Random(42L));

    UUID first = UUID.fromString(generator.nextPostingId().value());
    UUID second = UUID.fromString(generator.nextPostingId().value());

    assertNotEquals(first, second);
  }

  @Test
  void nextPostingId_rejectsNegativeTimestamps() {
    UuidV7PostingIdGenerator generator = new UuidV7PostingIdGenerator(() -> -1L, new Random(1L));

    assertThrows(IllegalStateException.class, generator::nextPostingId);
  }

  @Test
  void nextPostingId_rejectsTimestampsOutsideThe48BitRange() {
    UuidV7PostingIdGenerator generator =
        new UuidV7PostingIdGenerator(() -> 0x0001_0000_0000_0000L, new Random(1L));

    assertThrows(IllegalStateException.class, generator::nextPostingId);
  }
}
