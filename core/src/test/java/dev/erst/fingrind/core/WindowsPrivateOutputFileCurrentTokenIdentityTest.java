package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Proves SID-authorized Windows token identities resolve their canonical native account names. */
class WindowsPrivateOutputFileCurrentTokenIdentityTest {
  @Test
  void currentTokenIdentityResolvesItsAccountFromTheNativeSid() throws Exception {
    try (CurrentTokenOwnerCalls calls = new CurrentTokenOwnerCalls()) {
      WindowsCurrentTokenUserIdentity identity =
          WindowsCurrentTokenUserIdentity.resolve(calls.callTable());

      assertEquals("S-1-5-21-42", identity.sidText());
      assertEquals("RUNNER\\runneradmin", identity.accountName());
      assertEquals(2, calls.accountLookupCount());
    }
  }

  @Test
  void currentTokenIdentityFailsClosedForInvalidNativeAccountResolution() throws Exception {
    assertCurrentTokenIdentityRejected(
        calls ->
            calls.initialAccountLookupResult = new WindowsPrivateOutputFileNative.Result<>(1, 0));
    assertCurrentTokenIdentityRejected(
        calls ->
            calls.initialAccountLookupResult = new WindowsPrivateOutputFileNative.Result<>(0, 5));
    assertCurrentTokenIdentityRejected(calls -> calls.accountDomainCharacters = -1);
    assertCurrentTokenIdentityRejected(
        calls ->
            calls.accountDomainCharacters =
                WindowsPrivateOutputFileNative.MAXIMUM_ACCOUNT_NAME_CHARACTERS + 1);
    assertCurrentTokenIdentityRejected(
        calls ->
            calls.finalAccountLookupResult = new WindowsPrivateOutputFileNative.Result<>(0, 6));
    assertCurrentTokenIdentityRejected(calls -> calls.accountName = "");
  }

  @Test
  void currentTokenIdentityAcceptsANameWithoutADomainAndBoundedNonterminatedNativeText()
      throws Exception {
    try (CurrentTokenOwnerCalls calls = new CurrentTokenOwnerCalls()) {
      calls.accountDomain = "";
      calls.accountNameTerminated = false;

      WindowsCurrentTokenUserIdentity identity =
          WindowsCurrentTokenUserIdentity.resolve(calls.callTable());

      assertEquals("runneradmin?", identity.accountName());
    }
    try (CurrentTokenOwnerCalls calls = new CurrentTokenOwnerCalls()) {
      calls.accountName = "Ā";

      WindowsCurrentTokenUserIdentity identity =
          WindowsCurrentTokenUserIdentity.resolve(calls.callTable());

      assertEquals("RUNNER\\Ā", identity.accountName());
    }
  }

  private static void assertCurrentTokenIdentityRejected(
      java.util.function.Consumer<CurrentTokenOwnerCalls> mutation) throws IOException {
    try (CurrentTokenOwnerCalls calls = new CurrentTokenOwnerCalls()) {
      mutation.accept(calls);
      assertThrows(
          IOException.class, () -> WindowsCurrentTokenUserIdentity.resolve(calls.callTable()));
    }
  }

  /** Minimal deterministic native owner-call model for account lookup behavior. */
  private static final class CurrentTokenOwnerCalls
      extends WindowsPrivateOutputFileCallTestSupport.OwnerCalls implements AutoCloseable {
    private static final int DEFAULT_LENGTH = Integer.MIN_VALUE;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment ownerSid = arena.allocate(Long.BYTES, Long.BYTES);
    private final MemorySegment sidText = wide("S-1-5-21-42");
    private WindowsPrivateOutputFileNative.Result<Integer> initialAccountLookupResult =
        insufficientBuffer();
    private WindowsPrivateOutputFileNative.Result<Integer> finalAccountLookupResult = success();
    private String accountDomain = "RUNNER";
    private String accountName = "runneradmin";
    private boolean accountNameTerminated = true;
    private int accountDomainCharacters = DEFAULT_LENGTH;
    private int accountNameCharacters = DEFAULT_LENGTH;
    private int accountLookupCount;

    WindowsPrivateOutputFileCalls callTable() {
      return new WindowsPrivateOutputFileCalls(
          new ClosingHandleCalls(),
          this,
          new WindowsPrivateOutputFileCallTestSupport.SecurityCalls());
    }

    int accountLookupCount() {
      return accountLookupCount;
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
      if (informationClass != WindowsPrivateOutputFileNative.TOKEN_USER) {
        throw new AssertionError("Unexpected GetTokenInformation class: " + informationClass);
      }
      returnedLength.set(ValueLayout.JAVA_INT, 0L, Long.BYTES);
      if (informationLength == 0) {
        return insufficientBuffer();
      }
      information.set(ValueLayout.ADDRESS, 0L, ownerSid);
      return success();
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
      return new WindowsPrivateOutputFileNative.Result<>(0L, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> convertSidToStringSidW(
        MemorySegment sid, MemorySegment sidTextOut) {
      sidTextOut.set(ValueLayout.ADDRESS, 0L, sidText);
      return success();
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> lookupAccountSidW(
        MemorySegment systemName,
        MemorySegment sid,
        MemorySegment referencedDomainName,
        MemorySegment referencedDomainNameCharacters,
        MemorySegment accountNameOut,
        MemorySegment accountNameCharacters,
        MemorySegment sidNameUse) {
      accountLookupCount++;
      int domainLength = configuredLength(accountDomainCharacters, accountDomain);
      int nameLength = configuredLength(this.accountNameCharacters, accountName);
      referencedDomainNameCharacters.set(ValueLayout.JAVA_INT, 0L, domainLength);
      accountNameCharacters.set(ValueLayout.JAVA_INT, 0L, nameLength);
      if (referencedDomainName.address() == 0L && accountNameOut.address() == 0L) {
        return initialAccountLookupResult;
      }
      if (finalAccountLookupResult.value() != 0) {
        writeWide(referencedDomainName, accountDomain, true);
        writeWide(accountNameOut, accountName, accountNameTerminated);
      }
      return finalAccountLookupResult;
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

    private static int configuredLength(int configuredLength, String value) {
      return configuredLength == DEFAULT_LENGTH ? value.length() + 1 : configuredLength;
    }

    private static void writeWide(MemorySegment destination, String value, boolean terminated) {
      if (!terminated) {
        for (long index = 0L; index < destination.byteSize(); index += Character.BYTES) {
          destination.set(ValueLayout.JAVA_BYTE, index, (byte) '?');
          destination.set(ValueLayout.JAVA_BYTE, index + 1L, (byte) 0);
        }
      }
      byte[] bytes = (terminated ? value + "\0" : value).getBytes(StandardCharsets.UTF_16LE);
      destination.asSlice(0L, bytes.length).asByteBuffer().put(bytes);
    }
  }

  /** Closes the owner token successfully while every unneeded native call remains forbidden. */
  private static final class ClosingHandleCalls
      extends WindowsPrivateOutputFileCallTestSupport.HandleCalls {
    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle) {
      return success();
    }
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> success() {
    return new WindowsPrivateOutputFileNative.Result<>(1, 0);
  }

  private static WindowsPrivateOutputFileNative.Result<Integer> insufficientBuffer() {
    return new WindowsPrivateOutputFileNative.Result<>(
        0, WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER);
  }
}
