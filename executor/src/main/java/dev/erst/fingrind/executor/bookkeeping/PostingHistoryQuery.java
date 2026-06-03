package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Local bookkeeping query for one filtered posting-history page. */
public record PostingHistoryQuery(
    Optional<AccountCode> accountCode,
    EffectiveDateRange effectiveDateRange,
    int limit,
    Optional<PostingHistoryCursor> cursor) {
  public PostingHistoryQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(cursor, "cursor");
    if (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
        || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "Posting-history limit must be between "
              + ProtocolInteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + ProtocolInteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }

  /** Convenience constructor that lifts nullable bounds into the shared-kernel date range. */
  public PostingHistoryQuery(
      Optional<AccountCode> accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      int limit,
      Optional<PostingHistoryCursor> cursor) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), limit, cursor);
  }
}
