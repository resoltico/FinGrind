package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Policy seam that derives comparative reporting windows for one selected book. */
@NullMarked
public interface StatementComparativePolicy {
  /** Returns the comparative as-of window for one financial-position or trial-balance query. */
  EffectiveDateRange comparativeAsOf(
      BookIdentity bookIdentity, Optional<LocalDate> effectiveDateTo);

  /** Returns the comparative bounded period for one period-based statement query. */
  EffectiveDateRange comparativePeriod(
      BookIdentity bookIdentity, LocalDate effectiveDateFrom, LocalDate effectiveDateTo);

  /** Validates one required book identity before comparative derivation. */
  static BookIdentity requireBookIdentity(BookIdentity bookIdentity) {
    return Objects.requireNonNull(bookIdentity, "bookIdentity");
  }
}
