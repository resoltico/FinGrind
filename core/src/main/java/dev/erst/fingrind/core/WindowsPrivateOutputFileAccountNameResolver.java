package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Resolves one token SID to its canonical Windows account name through LookupAccountSidW. */
final class WindowsPrivateOutputFileAccountNameResolver {
  private WindowsPrivateOutputFileAccountNameResolver() {}

  static String resolve(WindowsPrivateOutputFileOwnerCalls calls, MemorySegment sid)
      throws IOException {
    WindowsPrivateOutputFileOwnerCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    MemorySegment checkedSid = Objects.requireNonNull(sid, "sid");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment domainCharacters = arena.allocate(ValueLayout.JAVA_INT);
      MemorySegment nameCharacters = arena.allocate(ValueLayout.JAVA_INT);
      MemorySegment sidNameUse = arena.allocate(ValueLayout.JAVA_INT);
      WindowsPrivateOutputFileNative.Result<Integer> initial =
          checkedCalls.lookupAccountSidW(
              MemorySegment.NULL,
              checkedSid,
              MemorySegment.NULL,
              domainCharacters,
              MemorySegment.NULL,
              nameCharacters,
              sidNameUse);
      if (initial.value() != 0
          || initial.lastError() != WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER) {
        throw WindowsPrivateOutputFileNative.windowsFailure(
            "LookupAccountSidW", initial.lastError());
      }
      int domainCharacterCount = domainCharacters.get(ValueLayout.JAVA_INT, 0L);
      int nameCharacterCount = nameCharacters.get(ValueLayout.JAVA_INT, 0L);
      requireAccountNameLength(domainCharacterCount, "domain");
      requireAccountNameLength(nameCharacterCount, "name");
      MemorySegment domain = allocateWideCharacters(arena, domainCharacterCount);
      MemorySegment name = allocateWideCharacters(arena, nameCharacterCount);
      WindowsPrivateOutputFileNative.requireTrue(
          checkedCalls.lookupAccountSidW(
              MemorySegment.NULL,
              checkedSid,
              domain,
              domainCharacters,
              name,
              nameCharacters,
              sidNameUse),
          "LookupAccountSidW");
      String resolvedName = wideText(name, nameCharacterCount);
      String resolvedDomain = wideText(domain, domainCharacterCount);
      if (resolvedName.isEmpty()) {
        throw new IOException("LookupAccountSidW returned an empty account name.");
      }
      return resolvedDomain.isEmpty()
          ? resolvedName
          : String.join("\\", resolvedDomain, resolvedName);
    }
  }

  private static void requireAccountNameLength(int length, String component) throws IOException {
    if (length < 0 || length > WindowsPrivateOutputFileNative.MAXIMUM_ACCOUNT_NAME_CHARACTERS) {
      throw new IOException(
          "LookupAccountSidW returned an invalid %s length.".formatted(component));
    }
  }

  private static MemorySegment allocateWideCharacters(Arena arena, int characterCount) {
    return arena.allocate(Math.max(1, characterCount) * (long) Character.BYTES, Character.BYTES);
  }

  private static String wideText(MemorySegment text, int characterCount) {
    byte[] bytes =
        text.reinterpret((long) Math.max(1, characterCount) * Character.BYTES)
            .toArray(ValueLayout.JAVA_BYTE);
    int byteLength = bytes.length;
    for (int index = 0; index + 1 < bytes.length; index += Character.BYTES) {
      if (bytes[index] == 0 && bytes[index + 1] == 0) {
        byteLength = index;
        break;
      }
    }
    return new String(bytes, 0, byteLength, StandardCharsets.UTF_16LE);
  }
}
