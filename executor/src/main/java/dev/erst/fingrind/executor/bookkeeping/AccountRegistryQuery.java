package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.InteractionLimits;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping query for one paginated account-registry slice. */
public record AccountRegistryQuery(int limit, Optional<AccountRegistryCursor> cursor) {
  public AccountRegistryQuery {
    Objects.requireNonNull(cursor, "cursor");
    if (limit < InteractionLimits.PAGE_LIMIT_MIN || limit > InteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "Account-registry limit must be between "
              + InteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + InteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }
}
