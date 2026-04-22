package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Stable cursor for keyset pagination through ascending account-code order. */
public record AccountPageCursor(AccountCode accountCode) {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES = Byte.BYTES + Integer.BYTES;

  /** Validates one account-page cursor. */
  public AccountPageCursor {
    Objects.requireNonNull(accountCode, "accountCode");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    byte[] accountCodeBytes = accountCode.value().getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + accountCodeBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putInt(accountCodeBytes.length);
    buffer.put(accountCodeBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  /** Parses one stable public wire value. */
  public static AccountPageCursor fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    ByteBuffer buffer = ByteBuffer.wrap(decodeWireValue(wireValue));
    if (buffer.remaining() < FIXED_CURSOR_BYTES || buffer.get() != CURSOR_FORMAT_VERSION) {
      throw unsupportedCursor(wireValue);
    }
    int accountCodeLength = buffer.getInt();
    if (accountCodeLength < 0 || buffer.remaining() != accountCodeLength) {
      throw unsupportedCursor(wireValue);
    }
    byte[] accountCodeBytes = new byte[accountCodeLength];
    buffer.get(accountCodeBytes);
    return new AccountPageCursor(
        new AccountCode(new String(accountCodeBytes, StandardCharsets.UTF_8)));
  }

  /** Creates one cursor anchored at the supplied declared account. */
  public static AccountPageCursor fromAccount(DeclaredAccount account) {
    Objects.requireNonNull(account, "account");
    return new AccountPageCursor(account.accountCode());
  }

  private static byte[] decodeWireValue(String wireValue) {
    try {
      return Base64.getUrlDecoder().decode(wireValue);
    } catch (IllegalArgumentException exception) {
      throw unsupportedCursor(wireValue, exception);
    }
  }

  private static IllegalArgumentException unsupportedCursor(String wireValue) {
    return new IllegalArgumentException("Unsupported account page cursor: " + wireValue);
  }

  private static IllegalArgumentException unsupportedCursor(String wireValue, Exception cause) {
    return new IllegalArgumentException("Unsupported account page cursor: " + wireValue, cause);
  }
}
