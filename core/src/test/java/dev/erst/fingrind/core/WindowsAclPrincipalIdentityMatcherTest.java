package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.UserPrincipal;
import org.junit.jupiter.api.Test;

/** Proves ACL owner identity comparisons use native SIDs rather than principal display names. */
class WindowsAclPrincipalIdentityMatcherTest {
  private static final UserPrincipal FIRST_ALIAS = () -> "first-alias";
  private static final UserPrincipal SECOND_ALIAS = () -> "second-alias";
  private static final UserPrincipal OTHER_ALIAS = () -> "other-alias";
  private static final UserPrincipal UNRESOLVABLE_ALIAS = () -> "unresolvable-alias";
  private static final UserPrincipal FIRST_SID = () -> "S-1-5-21-42";
  private static final UserPrincipal OTHER_SID = () -> "S-1-5-21-84";
  private static final UserPrincipal BLANK_PRINCIPAL = () -> " ";

  @Test
  void matchesOnlyPrincipalsWhoseResolvedNativeSidsAreEqual() throws Exception {
    try (NativeIdentityCalls calls = new NativeIdentityCalls()) {
      WindowsPrivateOutputFilePlatformAdapter adapter =
          new WindowsPrivateOutputFilePlatformAdapter(calls::callTable);

      assertTrue(adapter.matchesAclPrincipalIdentity(FIRST_ALIAS, SECOND_ALIAS));
      assertTrue(
          WindowsAclPrincipalIdentityMatcher.matches(calls.callTable(), FIRST_SID, FIRST_SID));
      assertFalse(
          WindowsAclPrincipalIdentityMatcher.matches(calls.callTable(), FIRST_ALIAS, OTHER_ALIAS));
      assertFalse(
          WindowsAclPrincipalIdentityMatcher.matches(calls.callTable(), FIRST_SID, OTHER_SID));
      assertFalse(
          WindowsAclPrincipalIdentityMatcher.matches(
              calls.callTable(), UNRESOLVABLE_ALIAS, FIRST_ALIAS));
      assertFalse(
          WindowsAclPrincipalIdentityMatcher.matches(
              calls.callTable(), FIRST_ALIAS, UNRESOLVABLE_ALIAS));
      assertFalse(
          WindowsAclPrincipalIdentityMatcher.matches(
              calls.callTable(), BLANK_PRINCIPAL, FIRST_ALIAS));
      assertEquals(12, calls.accountLookupCount());
      assertEquals(5, calls.localFreeCount());
    }
  }

  @Test
  void rejectsNullIdentityInputsBeforeNativeResolution() throws Exception {
    try (NativeIdentityCalls calls = new NativeIdentityCalls()) {
      assertThrows(
          NullPointerException.class,
          () -> WindowsAclPrincipalIdentityMatcher.matches(nullOf(), FIRST_ALIAS, SECOND_ALIAS));
      assertThrows(
          NullPointerException.class,
          () ->
              WindowsAclPrincipalIdentityMatcher.matches(
                  calls.callTable(), nullOf(), SECOND_ALIAS));
      assertThrows(
          NullPointerException.class,
          () ->
              WindowsAclPrincipalIdentityMatcher.matches(calls.callTable(), FIRST_ALIAS, nullOf()));
      assertEquals(0, calls.accountLookupCount());
    }
  }

  /** Deterministic native model with aliases that deliberately share or differ in their SIDs. */
  private static final class NativeIdentityCalls
      extends WindowsPrivateOutputFileCallTestSupport.OwnerCalls implements AutoCloseable {
    private final Arena arena = Arena.ofShared();
    private final MemorySegment firstSidText = wide(FIRST_SID.getName());
    private final MemorySegment otherSidText = wide(OTHER_SID.getName());
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
      sidTextOut.set(
          ValueLayout.ADDRESS,
          0L,
          sid.get(ValueLayout.JAVA_BYTE, 0L) == 1 ? firstSidText : otherSidText);
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
      if (UNRESOLVABLE_ALIAS.getName().equals(observedAccountName)) {
        return failure(WindowsPrivateOutputFileNative.ERROR_NONE_MAPPED);
      }
      sidBytes.set(ValueLayout.JAVA_INT, 0L, Integer.BYTES);
      referencedDomainNameCharacters.set(ValueLayout.JAVA_INT, 0L, 1);
      if (sid.address() == 0L) {
        return insufficientBuffer();
      }
      sid.set(
          ValueLayout.JAVA_BYTE,
          0L,
          FIRST_ALIAS.getName().equals(observedAccountName)
                  || SECOND_ALIAS.getName().equals(observedAccountName)
              ? (byte) 1
              : (byte) 2);
      return success();
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

    private static WindowsPrivateOutputFileNative.Result<Integer> success() {
      return new WindowsPrivateOutputFileNative.Result<>(1, 0);
    }

    private static WindowsPrivateOutputFileNative.Result<Integer> failure(int error) {
      return new WindowsPrivateOutputFileNative.Result<>(0, error);
    }

    private static WindowsPrivateOutputFileNative.Result<Integer> insufficientBuffer() {
      return failure(WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
    }
  }
}
