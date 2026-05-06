package dev.erst.fingrind.executor.bookkeeping;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping page of registered accounts. */
public record AccountRegistryPage(
    List<RegisteredAccount> accounts, int limit, Optional<AccountRegistryCursor> nextCursor) {
  /** Validates one local bookkeeping page of registered accounts. */
  public AccountRegistryPage {
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(nextCursor, "nextCursor");
    accounts = List.copyOf(accounts);
  }

  /** Returns whether one further account-registry page is available. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
