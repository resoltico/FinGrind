package dev.erst.fingrind.contract.bookkeeping;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Objects;

/** Shared stable wire codec for page cursors ordered by date, time, and a text identifier. */
final class KeysetPageCursorCodec {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES =
      Byte.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;

  private KeysetPageCursorCodec() {}

  static String encode(LocalDate effectiveDate, Instant recordedAt, String identifier) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(identifier, "identifier");
    byte[] identifierBytes = identifier.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + identifierBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putLong(effectiveDate.toEpochDay());
    buffer.putLong(recordedAt.getEpochSecond());
    buffer.putInt(recordedAt.getNano());
    buffer.putInt(identifierBytes.length);
    buffer.put(identifierBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  static Parts decode(String wireValue, String unsupportedCursorLabel) {
    Objects.requireNonNull(wireValue, "wireValue");
    Objects.requireNonNull(unsupportedCursorLabel, "unsupportedCursorLabel");
    ByteBuffer buffer = ByteBuffer.wrap(decodeWireValue(wireValue, unsupportedCursorLabel));
    if (buffer.remaining() < FIXED_CURSOR_BYTES || buffer.get() != CURSOR_FORMAT_VERSION) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel);
    }
    long epochDay = buffer.getLong();
    long epochSecond = buffer.getLong();
    int nano = buffer.getInt();
    int identifierLength = buffer.getInt();
    if (identifierLength < 0 || buffer.remaining() != identifierLength) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel);
    }
    byte[] identifierBytes = new byte[identifierLength];
    buffer.get(identifierBytes);
    return new Parts(
        dateFromEpochDay(wireValue, unsupportedCursorLabel, epochDay),
        instantFromEpochSecond(wireValue, unsupportedCursorLabel, epochSecond, nano),
        new String(identifierBytes, StandardCharsets.UTF_8));
  }

  record Parts(LocalDate effectiveDate, Instant recordedAt, String identifier) {}

  private static byte[] decodeWireValue(String wireValue, String unsupportedCursorLabel) {
    try {
      return Base64.getUrlDecoder().decode(wireValue);
    } catch (IllegalArgumentException exception) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel, exception);
    }
  }

  private static LocalDate dateFromEpochDay(
      String wireValue, String unsupportedCursorLabel, long epochDay) {
    try {
      return LocalDate.ofEpochDay(epochDay);
    } catch (DateTimeException exception) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel, exception);
    }
  }

  private static Instant instantFromEpochSecond(
      String wireValue, String unsupportedCursorLabel, long epochSecond, int nano) {
    try {
      return Instant.ofEpochSecond(epochSecond, nano);
    } catch (DateTimeException exception) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel, exception);
    }
  }

  private static IllegalArgumentException unsupportedCursor(
      String wireValue, String unsupportedCursorLabel) {
    return new IllegalArgumentException(unsupportedCursorLabel + ": " + wireValue);
  }

  private static IllegalArgumentException unsupportedCursor(
      String wireValue, String unsupportedCursorLabel, Exception cause) {
    return new IllegalArgumentException(unsupportedCursorLabel + ": " + wireValue, cause);
  }
}
