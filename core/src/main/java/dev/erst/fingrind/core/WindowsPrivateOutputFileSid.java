package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.UserPrincipal;
import java.util.Objects;
import java.util.Optional;

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

  /**
   * Resolves an observed ACL principal to its stable SID text without trusting its display name.
   */
  static Optional<String> resolveText(
      WindowsPrivateOutputFileOwnerCalls calls, UserPrincipal principal) throws IOException {
    WindowsPrivateOutputFileOwnerCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    UserPrincipal checkedPrincipal = Objects.requireNonNull(principal, "principal");
    String accountName = Objects.requireNonNull(checkedPrincipal.getName(), "ACL principal name");
    if (accountName.isBlank()) {
      return Optional.empty();
    }
    if (isText(accountName)) {
      return Optional.of(accountName);
    }
    try (WindowsPrivateOutputFileOperationArena operationArena =
        new WindowsPrivateOutputFileOperationArena(Arena.ofConfined())) {
      Optional<MemorySegment> sid =
          WindowsPrivateOutputFileAccountSidResolver.resolve(
              checkedCalls, operationArena.arena(), accountName);
      MemorySegment resolvedSid = sid.orElse(null);
      if (resolvedSid == null) {
        return Optional.empty();
      }
      return Optional.of(toText(checkedCalls, resolvedSid));
    }
  }

  static boolean isText(String value) {
    return value.length() > 2
        && value.charAt(0) == 'S'
        && value.charAt(1) == '-'
        && value.indexOf(' ') < 0;
  }
}
