package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.SingleTextPageCursorCodec;
import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Stable cursor for keyset pagination through ascending account-code order. */
public record AccountPageCursor(AccountCode accountCode) {
  /** Validates one account-page cursor. */
  public AccountPageCursor {
    Objects.requireNonNull(accountCode, "accountCode");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    return SingleTextPageCursorCodec.encode(accountCode.value());
  }

  /** Parses one stable public wire value. */
  public static AccountPageCursor fromWireValue(String wireValue) {
    return new AccountPageCursor(
        new AccountCode(
            SingleTextPageCursorCodec.decode(wireValue, "Unsupported account page cursor")));
  }

  /** Creates one cursor anchored at the supplied declared account. */
  public static AccountPageCursor fromAccount(DeclaredAccount account) {
    Objects.requireNonNull(account, "account");
    return new AccountPageCursor(account.accountCode());
  }
}
