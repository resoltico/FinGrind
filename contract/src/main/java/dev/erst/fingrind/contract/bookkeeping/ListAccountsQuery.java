package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.util.Objects;
import java.util.Optional;

/** One paginated query for the declared account registry. */
public record ListAccountsQuery(int limit, Optional<AccountPageCursor> cursor) {
  /** Validates one paginated account-list request. */
  public ListAccountsQuery {
    Objects.requireNonNull(cursor, "cursor");
    if (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
        || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "listAccounts limit must be between "
              + ProtocolInteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolInteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }
}
