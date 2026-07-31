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
import org.junit.jupiter.api.Test;

/** Proves Windows trust-boundary admission compares native SIDs rather than principal spellings. */
class WindowsTrustedAclPrincipalMatcherTest {
  private static final UserPrincipal ADMINISTRATORS_ALIAS = () -> "localized-administrators";
  private static final UserPrincipal TRUSTED_INSTALLER_ALIAS = () -> "localized-trusted-installer";
  private static final UserPrincipal OTHER_ACCOUNT = () -> "ordinary-account";
  private static final UserPrincipal UNRESOLVABLE_ACCOUNT = () -> "unresolvable-account";
  private static final UserPrincipal BLANK_ACCOUNT = () -> " ";
  private static final UserPrincipal ADMINISTRATORS_SID = () -> "S-1-5-32-544";
  private static final UserPrincipal LOWERCASE_ADMINISTRATORS_SID = () -> "s-1-5-32-544";
  private static final UserPrincipal LOCAL_SYSTEM_SID = () -> "S-1-5-18";
  private static final UserPrincipal TRUSTED_INSTALLER_SID =
      () -> "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464";
  private static final UserPrincipal OTHER_SID = () -> "S-1-5-21-42";
  private static final UserPrincipal MALFORMED_SID_LIKE_NAME = () -> "S-1 5";

  @Test
  void matchesOnlyFixedTrustBoundarySids() throws Exception {
    try (NativeTrustedPrincipalCalls calls = new NativeTrustedPrincipalCalls()) {
      WindowsPrivateOutputFilePlatformAdapter adapter =
          new WindowsPrivateOutputFilePlatformAdapter(calls::callTable);
      assertTrue(adapter.matchesTrustedAclPrincipal(ADMINISTRATORS_ALIAS));
      assertTrue(adapter.matchesTrustedAclPrincipal(TRUSTED_INSTALLER_ALIAS));
      assertTrue(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), ADMINISTRATORS_SID));
      assertTrue(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(
              calls.callTable(), LOWERCASE_ADMINISTRATORS_SID));
      assertTrue(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), LOCAL_SYSTEM_SID));
      assertTrue(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(
              calls.callTable(), TRUSTED_INSTALLER_SID));
      assertFalse(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), OTHER_ACCOUNT));
      assertFalse(WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), OTHER_SID));
      assertFalse(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(
              calls.callTable(), MALFORMED_SID_LIKE_NAME));
      assertFalse(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(
              calls.callTable(), UNRESOLVABLE_ACCOUNT));
      assertFalse(
          WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), BLANK_ACCOUNT));
      assertEquals(9, calls.accountLookupCount());
      assertEquals(4, calls.localFreeCount());
    }
  }

  @Test
  void rejectsAbsentOrMalformedNativeAccountResolution() throws Exception {
    try (NativeTrustedPrincipalCalls calls = new NativeTrustedPrincipalCalls()) {
      assertThrows(
          NullPointerException.class,
          () -> WindowsTrustedAclPrincipalMatcher.matchesTrusted(calls.callTable(), nullOf()));
      assertThrows(
          NullPointerException.class,
          () ->
              WindowsTrustedAclPrincipalMatcher.matchesTrusted(
                  calls.callTable(), (UserPrincipal) () -> null));

      calls.initialLookupResult = success();
      assertThrows(
          IOException.class,
          () ->
              WindowsTrustedAclPrincipalMatcher.matchesTrusted(
                  calls.callTable(), ADMINISTRATORS_ALIAS));
      calls.initialLookupResult = insufficientBuffer();
      calls.sidByteCount = 0;
      assertThrows(
          IOException.class,
          () ->
              WindowsTrustedAclPrincipalMatcher.matchesTrusted(
                  calls.callTable(), ADMINISTRATORS_ALIAS));
      calls.sidByteCount = Integer.BYTES;
      calls.convertSidToStringResult = failure(7);
      assertThrows(
          IOException.class,
          () ->
              WindowsTrustedAclPrincipalMatcher.matchesTrusted(
                  calls.callTable(), ADMINISTRATORS_ALIAS));
      calls.convertSidToStringResult = success();
      calls.finalLookupResult = failure(6);
      assertThrows(
          IOException.class,
          () ->
              WindowsTrustedAclPrincipalMatcher.matchesTrusted(
                  calls.callTable(), ADMINISTRATORS_ALIAS));
    }
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> success() {
    return new WindowsPrivateOutputFileNative.Result<>(1, 0);
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> failure(int error) {
    return new WindowsPrivateOutputFileNative.Result<>(0, error);
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> insufficientBuffer() {
    return failure(WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
  }

  /** Deterministic Win32 model with account aliases that resolve to native SID markers. */
  private static final class NativeTrustedPrincipalCalls
      extends WindowsPrivateOutputFileCallTestSupport.OwnerCalls implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment administratorsSidText = wide("S-1-5-32-544");
    private final MemorySegment trustedInstallerSidText =
        wide("S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464");
    private final MemorySegment otherSidText = wide("S-1-5-21-42");
    private WindowsPrivateOutputFileNative.Result<Integer> initialLookupResult =
        insufficientBuffer();
    private WindowsPrivateOutputFileNative.Result<Integer> finalLookupResult = success();
    private WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringResult = success();
    private int sidByteCount = Integer.BYTES;
    private int accountLookupCount;
    private int localFreeCount;

    WindowsPrivateOutputFileCalls callTable() {
      return new WindowsPrivateOutputFileCalls(
          new WindowsPrivateOutputFileCallTestSupport.HandleCalls(),
          this,
          new WindowsPrivateOutputFileCallTestSupport.SecurityCalls());
    }

    int accountLookupCount() {
      return accountLookupCount;
    }

    int localFreeCount() {
      return localFreeCount;
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
      localFreeCount++;
      return new WindowsPrivateOutputFileNative.Result<>(0L, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
        MemorySegment sid, MemorySegment sidTextOut) {
      if (convertSidToStringResult.value() == 0) {
        return convertSidToStringResult;
      }
      byte marker = sid.get(ValueLayout.JAVA_BYTE, 0L);
      MemorySegment sidText =
          marker == 1
              ? administratorsSidText
              : marker == 2 ? otherSidText : trustedInstallerSidText;
      sidTextOut.set(ValueLayout.ADDRESS, 0L, sidText);
      return convertSidToStringResult;
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
      if (UNRESOLVABLE_ACCOUNT.getName().equals(observedAccountName)) {
        return failure(WindowsPrivateOutputFileNative.ERROR_NONE_MAPPED);
      }
      sidBytes.set(ValueLayout.JAVA_INT, 0L, sidByteCount);
      referencedDomainNameCharacters.set(ValueLayout.JAVA_INT, 0L, 1);
      if (sid.address() == 0L) {
        return initialLookupResult;
      }
      if (finalLookupResult.value() != 0) {
        byte marker =
            ADMINISTRATORS_ALIAS.getName().equals(observedAccountName)
                ? (byte) 1
                : TRUSTED_INSTALLER_ALIAS.getName().equals(observedAccountName)
                    ? (byte) 3
                    : (byte) 2;
        sid.set(ValueLayout.JAVA_BYTE, 0L, marker);
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
  }
}
