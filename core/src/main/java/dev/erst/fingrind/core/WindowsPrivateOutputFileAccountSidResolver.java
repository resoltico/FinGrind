package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.Optional;

/** Resolves an ACL principal's Windows account representation to its native SID. */
final class WindowsPrivateOutputFileAccountSidResolver {
  private WindowsPrivateOutputFileAccountSidResolver() {}

  /**
   * Resolves the supplied account name, returning empty only when Windows reports no such account.
   *
   * <p>The returned SID is allocated in {@code arena}; its caller owns the arena's lifetime.
   */
  static Optional<MemorySegment> resolve(
      WindowsPrivateOutputFileOwnerCalls calls, Arena arena, String accountName)
      throws IOException {
    WindowsPrivateOutputFileOwnerCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    Arena checkedArena = Objects.requireNonNull(arena, "arena");
    String checkedAccountName = Objects.requireNonNull(accountName, "accountName");
    MemorySegment sidBytes = checkedArena.allocate(ValueLayout.JAVA_INT);
    MemorySegment domainCharacters = checkedArena.allocate(ValueLayout.JAVA_INT);
    MemorySegment sidNameUse = checkedArena.allocate(ValueLayout.JAVA_INT);
    WindowsPrivateOutputFileNative.Result<Integer> initial =
        checkedCalls.lookupAccountNameW(
            MemorySegment.NULL,
            WindowsPrivateOutputFileNative.wideString(checkedArena, checkedAccountName),
            MemorySegment.NULL,
            sidBytes,
            MemorySegment.NULL,
            domainCharacters,
            sidNameUse);
    if (initial.value() == 0
        && initial.lastError() == WindowsPrivateOutputFileNative.ERROR_NONE_MAPPED) {
      return Optional.empty();
    }
    if (initial.value() != 0
        || initial.lastError() != WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER) {
      throw WindowsPrivateOutputFileNative.windowsFailure(
          "LookupAccountNameW", initial.lastError());
    }
    int sidByteCount = sidBytes.get(ValueLayout.JAVA_INT, 0L);
    int domainCharacterCount = domainCharacters.get(ValueLayout.JAVA_INT, 0L);
    requireSidByteCount(sidByteCount);
    requireDomainCharacterCount(domainCharacterCount);
    MemorySegment sid = checkedArena.allocate(sidByteCount, ValueLayout.JAVA_INT.byteAlignment());
    MemorySegment domain =
        checkedArena.allocate(
            Math.max(1, domainCharacterCount) * (long) Character.BYTES, Character.BYTES);
    WindowsPrivateOutputFileNative.requireTrue(
        checkedCalls.lookupAccountNameW(
            MemorySegment.NULL,
            WindowsPrivateOutputFileNative.wideString(checkedArena, checkedAccountName),
            sid,
            sidBytes,
            domain,
            domainCharacters,
            sidNameUse),
        "LookupAccountNameW");
    return Optional.of(sid);
  }

  private static void requireSidByteCount(int sidByteCount) throws IOException {
    if (sidByteCount <= 0
        || sidByteCount > WindowsPrivateOutputFileNative.MAXIMUM_SID_BINARY_BYTES) {
      throw new IOException("LookupAccountNameW returned an invalid SID length.");
    }
  }

  private static void requireDomainCharacterCount(int domainCharacterCount) throws IOException {
    if (domainCharacterCount < 0
        || domainCharacterCount > WindowsPrivateOutputFileNative.MAXIMUM_ACCOUNT_NAME_CHARACTERS) {
      throw new IOException("LookupAccountNameW returned an invalid domain length.");
    }
  }
}
