package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Optional;

/** Filter and pagination request for listing committed postings. */
public record ListPostingsQuery(
    Optional<AccountCode> accountCode,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    int limit,
    int offset) {
  /** Validates one posting-list query. */
  public ListPostingsQuery {
    accountCode = accountCode == null ? Optional.empty() : accountCode;
    effectiveDateFrom = effectiveDateFrom == null ? Optional.empty() : effectiveDateFrom;
    effectiveDateTo = effectiveDateTo == null ? Optional.empty() : effectiveDateTo;
    if (limit < ProtocolLimits.PAGE_LIMIT_MIN || limit > ProtocolLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "Posting list limit must be between "
              + ProtocolLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolLimits.PAGE_LIMIT_MAX
              + ".");
    }
    if (offset < ProtocolLimits.PAGE_OFFSET_MIN) {
      throw new IllegalArgumentException("Posting list offset must not be negative.");
    }
    if (effectiveDateFrom.isPresent()
        && effectiveDateTo.isPresent()
        && effectiveDateFrom.orElseThrow().isAfter(effectiveDateTo.orElseThrow())) {
      throw new IllegalArgumentException(
          "Posting list effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }

  /** Returns the maximum supported posting-list page size. */
  public static int maxLimit() {
    return ProtocolLimits.PAGE_LIMIT_MAX;
  }
}
