package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Successful selection of the only valid active result-holding account. */
public final class AcceptedInterimResultTargetSelection implements InterimResultTargetSelection {
  private final RegisteredAccount account;

  /** Creates one accepted selection for the resolved result-holding account. */
  public AcceptedInterimResultTargetSelection(RegisteredAccount account) {
    this.account = Objects.requireNonNull(account, "account");
  }

  /** Returns the selected active result-holding account. */
  public RegisteredAccount account() {
    return account;
  }
}
