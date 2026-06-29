package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.util.Objects;
import java.util.Optional;

/** One paginated query for the declared tax-registration registry. */
public record ListTaxRegistrationsQuery(int limit, Optional<TaxRegistrationPageCursor> cursor) {
  /** Validates one paginated tax-registration list request. */
  public ListTaxRegistrationsQuery {
    Objects.requireNonNull(cursor, "cursor");
    if (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
        || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "listTaxRegistrations limit must be between "
              + ProtocolInteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolInteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }
}
