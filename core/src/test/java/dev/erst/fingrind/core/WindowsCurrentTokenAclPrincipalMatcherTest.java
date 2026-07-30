package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.UserPrincipal;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Proves current-token ACL admission compares native SIDs rather than display-name spellings. */
class WindowsCurrentTokenAclPrincipalMatcherTest {
  private static final UserPrincipal NATIVE_TOKEN_ALIAS = () -> "local-token-alias";
  private static final UserPrincipal DIFFERENT_USER = () -> "different-user";
  private static final UserPrincipal UNRESOLVABLE_USER = () -> "unresolvable-user";
  private static final UserPrincipal BLANK_USER = () -> " ";

  @Test
  void matchesOnlyTheObservedPrincipalWhoseNativeSidEqualsTheCurrentToken() throws Exception {
    try (NativeMatchCalls calls = new NativeMatchCalls()) {
      WindowsCurrentTokenAclPrincipalMatcher matcher =
          WindowsCurrentTokenAclPrincipalMatcher.acquire(calls.callTable());
      try {
        assertTrue(matcher.matchesCurrentToken(NATIVE_TOKEN_ALIAS));
        assertFalse(matcher.matchesCurrentToken(DIFFERENT_USER));
        assertFalse(matcher.matchesCurrentToken(UNRESOLVABLE_USER));
        assertFalse(matcher.matchesCurrentToken(BLANK_USER));
      } finally {
        matcher.release();
      }
      assertEquals(5, calls.accountLookupCount());
      assertEquals(1, calls.closedTokenCount());
    }
  }

  @Test
  void rejectsMalformedOrFailedNativeAccountResolution() throws IOException {
    assertMatcherRejected(calls -> calls.initialLookupResult = success());
    assertMatcherRejected(calls -> calls.initialLookupResult = failure(5));
    assertMatcherRejected(calls -> calls.sidByteCount = 0);
    assertMatcherRejected(
        calls -> calls.sidByteCount = WindowsPrivateOutputFileNative.MAXIMUM_SID_BINARY_BYTES + 1);
    assertMatcherRejected(calls -> calls.domainCharacterCount = -1);
    assertMatcherRejected(
        calls ->
            calls.domainCharacterCount =
                WindowsPrivateOutputFileNative.MAXIMUM_ACCOUNT_NAME_CHARACTERS + 1);
    assertMatcherRejected(calls -> calls.finalLookupResult = failure(6));
  }

  @Test
  void rejectsAbsentOrNullPrincipalInputBeforeNativeResolution() throws Exception {
    try (NativeMatchCalls calls = new NativeMatchCalls()) {
      WindowsCurrentTokenAclPrincipalMatcher matcher =
          WindowsCurrentTokenAclPrincipalMatcher.acquire(calls.callTable());
      try {
        assertThrows(NullPointerException.class, () -> matcher.matchesCurrentToken(nullOf()));
        assertThrows(
            NullPointerException.class,
            () -> matcher.matchesCurrentToken((UserPrincipal) () -> null));
      } finally {
        matcher.release();
      }
      assertEquals(0, calls.accountLookupCount());
      assertEquals(1, calls.closedTokenCount());
    }
  }

  @Test
  void releasesItsTokenExactlyOnceAndRejectsFurtherMatching() throws Exception {
    try (NativeMatchCalls calls = new NativeMatchCalls()) {
      WindowsCurrentTokenAclPrincipalMatcher matcher =
          WindowsCurrentTokenAclPrincipalMatcher.acquire(calls.callTable());
      matcher.release();
      matcher.release();

      assertThrows(
          IllegalStateException.class, () -> matcher.matchesCurrentToken(NATIVE_TOKEN_ALIAS));
      assertEquals(1, calls.closedTokenCount());
    }
  }

  private static void assertMatcherRejected(Consumer<NativeMatchCalls> mutation)
      throws IOException {
    try (NativeMatchCalls calls = new NativeMatchCalls()) {
      mutation.accept(calls);
      WindowsCurrentTokenAclPrincipalMatcher matcher =
          WindowsCurrentTokenAclPrincipalMatcher.acquire(calls.callTable());
      try {
        assertThrows(IOException.class, () -> matcher.matchesCurrentToken(NATIVE_TOKEN_ALIAS));
      } finally {
        matcher.release();
      }
      assertEquals(1, calls.closedTokenCount());
    }
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> success() {
    return new WindowsPrivateOutputFileNative.Result<>(1, 0);
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> failure(int error) {
    return new WindowsPrivateOutputFileNative.Result<>(0, error);
  }

  /** Deterministic Win32 model whose account aliases deliberately differ from their SIDs. */
  private static final class NativeMatchCalls
      extends WindowsPrivateOutputFileCallTestSupport.OwnerCalls implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment tokenSid = arena.allocate(Long.BYTES, Long.BYTES);
    private final MemorySegment tokenSidText = wide("S-1-5-21-42");
    private final ClosingHandleCalls handleCalls = new ClosingHandleCalls();
    private final WindowsPrivateOutputFileSecurityCalls securityCalls = new SidComparisonCalls();
    private WindowsPrivateOutputFileNative.Result<Integer> initialLookupResult =
        insufficientBuffer();
    private WindowsPrivateOutputFileNative.Result<Integer> finalLookupResult = success();
    private int sidByteCount = Integer.BYTES;
    private int domainCharacterCount = 1;
    private int accountLookupCount;

    WindowsPrivateOutputFileCalls callTable() {
      return new WindowsPrivateOutputFileCalls(handleCalls, this, securityCalls);
    }

    int accountLookupCount() {
      return accountLookupCount;
    }

    int closedTokenCount() {
      return handleCalls.closedTokenCount;
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> getCurrentProcess() {
      return new WindowsPrivateOutputFileNative.Result<>(0x11L, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> openProcessToken(
        MemorySegment process, int desiredAccess, MemorySegment token) {
      token.set(ValueLayout.ADDRESS, 0L, MemorySegment.ofAddress(0x22L));
      return success();
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getTokenInformation(
        MemorySegment token,
        int informationClass,
        MemorySegment information,
        int informationLength,
        MemorySegment returnedLength) {
      assertEquals(WindowsPrivateOutputFileNative.TOKEN_USER, informationClass);
      returnedLength.set(ValueLayout.JAVA_INT, 0L, Long.BYTES);
      if (informationLength == 0) {
        return insufficientBuffer();
      }
      information.set(ValueLayout.ADDRESS, 0L, tokenSid);
      return success();
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
      return new WindowsPrivateOutputFileNative.Result<>(0L, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
        MemorySegment sid, MemorySegment sidText) {
      sidText.set(ValueLayout.ADDRESS, 0L, tokenSidText);
      return success();
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> lookupAccountNameW(
        MemorySegment systemName,
        MemorySegment accountName,
        MemorySegment sid,
        MemorySegment sidBytes,
        MemorySegment referencedDomainName,
        MemorySegment referencedDomainNameCharacters,
        MemorySegment sidNameUse) {
      accountLookupCount++;
      String observedAccountName = wideText(accountName);
      if (UNRESOLVABLE_USER.getName().equals(observedAccountName)) {
        return failure(WindowsPrivateOutputFileNative.ERROR_NONE_MAPPED);
      }
      sidBytes.set(ValueLayout.JAVA_INT, 0L, sidByteCount);
      referencedDomainNameCharacters.set(ValueLayout.JAVA_INT, 0L, domainCharacterCount);
      if (sid.address() == 0L) {
        return initialLookupResult;
      }
      if (finalLookupResult.value() != 0) {
        sid.set(
            ValueLayout.JAVA_BYTE,
            0L,
            NATIVE_TOKEN_ALIAS.getName().equals(observedAccountName) ? (byte) 1 : (byte) 2);
      }
      return finalLookupResult;
    }

    @Override
    public void close() {
      arena.close();
    }

    private MemorySegment wide(String value) {
      byte[] bytes = (value + "\0").getBytes(StandardCharsets.UTF_16LE);
      MemorySegment result = arena.allocate(bytes.length, Character.BYTES);
      result.asByteBuffer().put(bytes);
      return result;
    }

    private static String wideText(MemorySegment text) {
      byte[] bytes = text.toArray(ValueLayout.JAVA_BYTE);
      int length = 0;
      while (length + 1 < bytes.length && (bytes[length] != 0 || bytes[length + 1] != 0)) {
        length += Character.BYTES;
      }
      return new String(bytes, 0, length, StandardCharsets.UTF_16LE);
    }

    private static WindowsPrivateOutputFileNative.Result<Integer> insufficientBuffer() {
      return failure(WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
    }

    /** Records native token-handle closure for the matcher lifetime assertions. */
    private static final class ClosingHandleCalls
        extends WindowsPrivateOutputFileCallTestSupport.HandleCalls {
      private int closedTokenCount;

      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle) {
        closedTokenCount++;
        return success();
      }
    }

    /** Compares the synthetic account SID marker with the fixed current-token SID marker. */
    private static final class SidComparisonCalls
        extends WindowsPrivateOutputFileCallTestSupport.SecurityCalls {
      @Override
      public WindowsPrivateOutputFileNative.Result<Integer> equalSid(
          MemorySegment firstSid, MemorySegment secondSid) {
        return new WindowsPrivateOutputFileNative.Result<>(
            secondSid.get(ValueLayout.JAVA_BYTE, 0L) == 1 ? 1 : 0, 0);
      }
    }
  }
}
