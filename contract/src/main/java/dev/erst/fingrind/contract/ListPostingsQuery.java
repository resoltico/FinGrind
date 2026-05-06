package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.InteractionLimits;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Filter and pagination request for listing committed postings. */
public record ListPostingsQuery(
    Optional<AccountCode> accountCode,
    EffectiveDateRange effectiveDateRange,
    int limit,
    Optional<PostingPageCursor> cursor) {
  /** Validates one posting-list query. */
  public ListPostingsQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(cursor, "cursor");
    if (limit < InteractionLimits.PAGE_LIMIT_MIN || limit > InteractionLimits.PAGE_LIMIT_MAX) {
      throw new IllegalArgumentException(
          "Posting list limit must be between "
              + InteractionLimits.PAGE_LIMIT_MIN
              + " and "
              + InteractionLimits.PAGE_LIMIT_MAX
              + ".");
    }
  }

  /** Convenience constructor that lifts nullable date bounds into a typed range. */
  public ListPostingsQuery(
      Optional<AccountCode> accountCode,
      @Nullable LocalDate effectiveDateFrom,
      @Nullable LocalDate effectiveDateTo,
      int limit,
      Optional<PostingPageCursor> cursor) {
    this(accountCode, EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo), limit, cursor);
  }

  /** Returns the optional lower effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateFrom() {
    return effectiveDateRange.effectiveDateFrom();
  }

  /** Returns the optional upper effective-date bound carried by this query. */
  public Optional<LocalDate> effectiveDateTo() {
    return effectiveDateRange.effectiveDateTo();
  }

  /** Returns the maximum supported posting-list page size. */
  public static int maxLimit() {
    return InteractionLimits.PAGE_LIMIT_MAX;
  }
}
