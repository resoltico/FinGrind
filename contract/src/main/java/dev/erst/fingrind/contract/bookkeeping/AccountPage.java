package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stable page of declared accounts. */
public record AccountPage(
    List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
  /** Validates one paginated account page. */
  public AccountPage(
      List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
    this.accounts = ContractDescriptorValidation.copyList(accounts, "accounts");
    this.limit = limit;
    this.nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
    if (limit < 1) {
      throw new IllegalArgumentException("Account page limit must be positive.");
    }
  }

  /** Returns whether another page exists after this one. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
