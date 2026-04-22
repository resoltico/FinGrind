package dev.erst.fingrind.contract;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One stable page of declared accounts. */
public record AccountPage(List<DeclaredAccount> accounts, int limit, int offset, boolean hasMore) {
  /** Validates one paginated account page. */
  public AccountPage(
      @Nullable List<DeclaredAccount> accounts, int limit, int offset, boolean hasMore) {
    this.accounts = accounts == null ? List.of() : List.copyOf(accounts);
    this.limit = limit;
    this.offset = offset;
    this.hasMore = hasMore;
    if (limit < 1) {
      throw new IllegalArgumentException("Account page limit must be positive.");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Account page offset must not be negative.");
    }
  }
}
