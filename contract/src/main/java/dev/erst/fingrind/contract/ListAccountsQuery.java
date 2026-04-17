package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolLimits;

/** One paginated query for the declared account registry. */
public record ListAccountsQuery(int limit, int offset) {
  /** Validates one paginated account-list request. */
  public ListAccountsQuery {
    if (limit < ProtocolLimits.PAGE_LIMIT_MIN || limit > ProtocolLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "listAccounts limit must be between "
              + ProtocolLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolLimits.PAGE_LIMIT_MAX
              + ".");
    }
    if (offset < ProtocolLimits.PAGE_OFFSET_MIN) {
      throw new IllegalArgumentException("listAccounts offset must not be negative.");
    }
  }
}
