package dev.erst.fingrind.contract;

import java.util.List;

/** One stable page of declared accounts. */
public record AccountPage(List<DeclaredAccount> accounts, int limit, int offset, boolean hasMore) {
  /** Validates one paginated account page. */
  public AccountPage {
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    if (limit < 1) {
      throw new IllegalArgumentException("Account page limit must be positive.");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Account page offset must not be negative.");
    }
  }
}
