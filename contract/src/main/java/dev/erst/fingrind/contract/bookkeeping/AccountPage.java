package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stable page of declared accounts. */
public record AccountPage(
    BookIdentity bookIdentity,
    List<DeclaredAccount> accounts,
    int limit,
    Optional<AccountPageCursor> nextCursor) {
  /** Validates one paginated account page. */
  public AccountPage(
      BookIdentity bookIdentity,
      List<DeclaredAccount> accounts,
      int limit,
      Optional<AccountPageCursor> nextCursor) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
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
