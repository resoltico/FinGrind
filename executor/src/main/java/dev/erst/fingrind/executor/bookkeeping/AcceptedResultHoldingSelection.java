package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Successful selection of the only valid active result-holding account. */
public final class AcceptedResultHoldingSelection implements ResultHoldingSelection {
  private final RegisteredAccount account;

  /** Creates one accepted selection for the resolved result-holding account. */
  public AcceptedResultHoldingSelection(RegisteredAccount account) {
    this.account = Objects.requireNonNull(account, "account");
  }

  /** Returns the selected active result-holding account. */
  public RegisteredAccount account() {
    return account;
  }
}
