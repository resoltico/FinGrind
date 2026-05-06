package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.InteractionLimits;
import java.util.Objects;
import java.util.Optional;

/** One paginated query for the declared account registry. */
public record ListAccountsQuery(int limit, Optional<AccountPageCursor> cursor) {
  /** Validates one paginated account-list request. */
  public ListAccountsQuery {
    Objects.requireNonNull(cursor, "cursor");
    if (limit < InteractionLimits.PAGE_LIMIT_MIN || limit > InteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "listAccounts limit must be between "
              + InteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + InteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }
}
