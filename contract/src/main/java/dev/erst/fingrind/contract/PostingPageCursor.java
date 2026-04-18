package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.PostingId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Objects;

/** Stable cursor for keyset-pagination through reverse-chronological posting history. */
public record PostingPageCursor(LocalDate effectiveDate, Instant recordedAt, PostingId postingId) {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES =
      Byte.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;

  /** Validates one posting-page cursor. */
  public PostingPageCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(postingId, "postingId");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    byte[] postingIdBytes = postingId.value().getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + postingIdBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putLong(effectiveDate.toEpochDay());
    buffer.putLong(recordedAt.getEpochSecond());
    buffer.putInt(recordedAt.getNano());
    buffer.putInt(postingIdBytes.length);
    buffer.put(postingIdBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  /** Parses one stable public wire value. */
  public static PostingPageCursor fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    ByteBuffer buffer = ByteBuffer.wrap(decodeWireValue(wireValue));
    if (buffer.remaining() < FIXED_CURSOR_BYTES || buffer.get() != CURSOR_FORMAT_VERSION) {
      throw unsupportedCursor(wireValue);
    }
    long epochDay = buffer.getLong();
    long epochSecond = buffer.getLong();
    int nano = buffer.getInt();
    int postingIdLength = buffer.getInt();
    if (postingIdLength < 0 || buffer.remaining() != postingIdLength) {
      throw unsupportedCursor(wireValue);
    }
    byte[] postingIdBytes = new byte[postingIdLength];
    buffer.get(postingIdBytes);
    return new PostingPageCursor(
        dateFromEpochDay(wireValue, epochDay),
        instantFromEpochSecond(wireValue, epochSecond, nano),
        new PostingId(new String(postingIdBytes, StandardCharsets.UTF_8)));
  }

  /** Creates one cursor anchored at the supplied committed posting. */
  public static PostingPageCursor fromPosting(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return new PostingPageCursor(
        postingFact.journalEntry().effectiveDate(),
        postingFact.provenance().recordedAt(),
        postingFact.postingId());
  }

  private static byte[] decodeWireValue(String wireValue) {
    try {
      return Base64.getUrlDecoder().decode(wireValue);
    } catch (IllegalArgumentException exception) {
      throw unsupportedCursor(wireValue, exception);
    }
  }

  private static LocalDate dateFromEpochDay(String wireValue, long epochDay) {
    try {
      return LocalDate.ofEpochDay(epochDay);
    } catch (DateTimeException exception) {
      throw unsupportedCursor(wireValue, exception);
    }
  }

  private static Instant instantFromEpochSecond(String wireValue, long epochSecond, int nano) {
    try {
      return Instant.ofEpochSecond(epochSecond, nano);
    } catch (DateTimeException exception) {
      throw unsupportedCursor(wireValue, exception);
    }
  }

  private static IllegalArgumentException unsupportedCursor(String wireValue) {
    return new IllegalArgumentException("Unsupported posting page cursor: " + wireValue);
  }

  private static IllegalArgumentException unsupportedCursor(String wireValue, Exception cause) {
    return new IllegalArgumentException("Unsupported posting page cursor: " + wireValue, cause);
  }
}
