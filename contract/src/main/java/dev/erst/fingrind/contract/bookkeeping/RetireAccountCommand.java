package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Application command that retires one zero-balance account from ordinary authored use. */
public record RetireAccountCommand(AccountCode accountCode) {
  /** Validates one account retirement request. */
  public RetireAccountCommand {
    Objects.requireNonNull(accountCode, "accountCode");
  }
}
