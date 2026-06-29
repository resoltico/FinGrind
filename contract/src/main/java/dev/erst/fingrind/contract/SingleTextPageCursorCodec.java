package dev.erst.fingrind.contract;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Shared stable wire codec for page cursors backed by one UTF-8 text identifier. */
@NullMarked
public final class SingleTextPageCursorCodec {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES = Byte.BYTES + Integer.BYTES;

  private SingleTextPageCursorCodec() {}

  /** Encodes one stable UTF-8 text identifier into the public cursor wire value. */
  public static String encode(String textValue) {
    Objects.requireNonNull(textValue, "textValue");
    byte[] textBytes = textValue.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + textBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putInt(textBytes.length);
    buffer.put(textBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  /** Decodes one public cursor wire value back into its stable UTF-8 text identifier. */
  public static String decode(String wireValue, String unsupportedCursorLabel) {
    Objects.requireNonNull(wireValue, "wireValue");
    Objects.requireNonNull(unsupportedCursorLabel, "unsupportedCursorLabel");
    ByteBuffer buffer = ByteBuffer.wrap(decodeWireValue(wireValue, unsupportedCursorLabel));
    if (buffer.remaining() < FIXED_CURSOR_BYTES || buffer.get() != CURSOR_FORMAT_VERSION) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel);
    }
    int textLength = buffer.getInt();
    if (textLength < 0 || buffer.remaining() != textLength) {
      throw unsupportedCursor(wireValue, unsupportedCursorLabel);
    }
    byte[] textBytes = new byte[textLength];
    buffer.get(textBytes);
    return new String(textBytes, StandardCharsets.UTF_8);
  }

  private static byte[] decodeWireValue(String wireValue, String unsupportedCursorLabel) {
    try {
      return Base64.getUrlDecoder().decode(wireValue);
    } catch (IllegalArgumentException exception) {
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
