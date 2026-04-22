package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** One stable page of declared accounts. */
public record AccountPage(
    List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
  /** Validates one paginated account page. */
  public AccountPage(
      @Nullable List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
    this.accounts = accounts == null ? List.of() : List.copyOf(accounts);
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
