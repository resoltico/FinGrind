package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Successful selection of the only valid active close-target account. */
public final class AcceptedCloseTargetSelection implements CloseTargetSelection {
  private final RegisteredAccount account;

  /** Creates one accepted selection for the resolved close-target account. */
  public AcceptedCloseTargetSelection(RegisteredAccount account) {
    this.account = Objects.requireNonNull(account, "account");
  }

  /** Returns the selected active close-target account. */
  public RegisteredAccount account() {
    return account;
  }
}
