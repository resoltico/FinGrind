package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Converts and recognizes native Windows security identifiers without account-name authority. */
final class WindowsPrivateOutputFileSid {
  private WindowsPrivateOutputFileSid() {}

  static String toText(WindowsPrivateOutputFileOwnerCalls calls, MemorySegment sid)
      throws IOException {
    WindowsPrivateOutputFileOwnerCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    MemorySegment checkedSid = Objects.requireNonNull(sid, "sid");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment textOut = arena.allocate(ValueLayout.ADDRESS);
      WindowsPrivateOutputFileNative.requireTrue(
          checkedCalls.convertSidToStringSidW(checkedSid, textOut), "ConvertSidToStringSidW");
      MemorySegment text = textOut.get(ValueLayout.ADDRESS, 0L);
      if (text.address() == 0L) {
        throw new IOException("ConvertSidToStringSidW returned no SID string.");
      }
      try {
        byte[] bytes =
            MemorySegment.ofAddress(text.address())
                .reinterpret(WindowsPrivateOutputFileNative.MAXIMUM_SID_STRING_BYTES)
                .toArray(ValueLayout.JAVA_BYTE);
        int length = 0;
        while (length + 1 < bytes.length && (bytes[length] != 0 || bytes[length + 1] != 0)) {
          length += Character.BYTES;
        }
        if (length + 1 >= bytes.length) {
          throw new IOException("Windows SID text exceeded its bounded buffer.");
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_16LE);
      } finally {
        WindowsPrivateOutputFileNative.localFree(checkedCalls, text);
      }
    }
  }

  static boolean isText(String value) {
    return value.length() > 2
        && value.charAt(0) == 'S'
        && value.charAt(1) == '-'
        && value.indexOf(' ') < 0;
  }
}
