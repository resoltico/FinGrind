package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Local bookkeeping cursor for ascending account-registry pagination. */
public record AccountRegistryCursor(AccountCode accountCode) {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES = Byte.BYTES + Integer.BYTES;

  public AccountRegistryCursor {
    Objects.requireNonNull(accountCode, "accountCode");
  }

  /** Returns the stable machine-facing wire value for this local cursor. */
  public String wireValue() {
    byte[] accountCodeBytes = accountCode.value().getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + accountCodeBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putInt(accountCodeBytes.length);
    buffer.put(accountCodeBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }
}
