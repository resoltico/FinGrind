package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping query for one paginated account-registry slice. */
public record AccountRegistryQuery(int limit, Optional<AccountRegistryCursor> cursor) {
  public AccountRegistryQuery {
    Objects.requireNonNull(cursor, "cursor");
    if (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
        || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "Account-registry limit must be between "
              + ProtocolInteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolInteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }
}
